package com.trace.product.service;

import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.extra.qrcode.QrCodeUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.trace.product.common.BusinessException;
import com.trace.product.common.ResultCode;
import com.trace.product.dto.BatchGenerateDTO;
import com.trace.product.dto.BatchTaskProgress;
import com.trace.product.dto.CodeWithProductVO;
import com.trace.product.dto.NormalCodeVO;
import com.trace.product.entity.BatchCodeTask;
import com.trace.product.entity.Product;
import com.trace.product.entity.ProductCode;
import com.trace.product.mapper.BatchCodeTaskMapper;
import com.trace.product.mapper.ProductCodeMapper;
import com.trace.product.mapper.ProductMapper;
import com.trace.product.storage.MediaStrategyFactory;
import com.trace.product.storage.MediaUploadResult;
import com.trace.product.util.CodeGenerator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.File;
import java.util.Collections;
import java.util.List;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 二维码生成与管理
 *
 * <p>普通产品码（TYPE_NORMAL）：Hutool QrCodeUtil 生成 H5 短链二维码（内容 /scan/{code}）
 * 一物一码（TYPE_UNIQUE）：Hutool QrCodeUtil 生成唯一 H5 短链二维码，异步批量入库 + 保存图片
 * 补码（generateQrImage）：为旧记录补生成图片，仍走 Hutool（不再走微信小程序接口）
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProductCodeService {

    private final ProductMapper productMapper;
    private final ProductCodeMapper productCodeMapper;
    private final BatchCodeTaskMapper batchCodeTaskMapper;
    private final MediaStrategyFactory mediaStrategyFactory;
    private final CodeGenerator codeGenerator;
    private final BatchCodeService batchCodeService;
    private final BatchProgressStore progressStore;
    private final RedisTemplate<String, Object> redisTemplate;

    private static final String META_KEY_PREFIX = "trace:code:meta:";
    private static final DateTimeFormatter BATCH_NO_FMT = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    /** H5 扫码落地页前缀，例 https://h5.your-domain.com/scan */
    @Value("${trace.h5.scan-base-url:}")
    private String scanBaseUrl;

    /** 一物一码默认扫码次数上限（前端未传 scanLimit 时用这个） */
    @Value("${trace.anti-fake.default-scan-limit:3}")
    private int defaultScanLimit;

    public record BatchCsvFile(File file, String fileName) {
    }

    /**
     * 码列表分页（JOIN 产品表带出基础信息）
     */
    public Page<CodeWithProductVO> pageWithProduct(long current, long size, Long productId,
                                                    Integer codeType, String batchNo, String keyword,
                                                    Integer status) {
        long total = productCodeMapper.countWithProduct(codeType, productId, batchNo, keyword, status);
        int offset = (int) ((current - 1) * size);
        List<CodeWithProductVO> records = productCodeMapper.selectPageWithProduct(
                codeType, productId, batchNo, keyword, status, offset, (int) size);
        Page<CodeWithProductVO> p = new Page<>(current, size, total);
        p.setRecords(records);
        return p;
    }

    /**
     * 修改单个码状态：1-有效 0-作废。
     * 必须同步清理 Redis 元数据缓存，否则最长 10 分钟内仍会被判为有效。
     */
    public void updateStatus(Long id, Integer status) {
        checkStatus(status);
        ProductCode code = productCodeMapper.selectById(id);
        if (code == null) {
            throw new BusinessException(ResultCode.CODE_NOT_FOUND);
        }
        changeStatus(Collections.singletonList(code), status);
    }

    /**
     * 删除单个码：先查记录拿到图片路径，删本地文件，清缓存，再逻辑删除数据库记录
     */
    @Transactional(rollbackFor = Exception.class)
    public void deleteCode(Long id) {
        ProductCode code = productCodeMapper.selectById(id);
        if (code == null) {
            throw new BusinessException(ResultCode.CODE_NOT_FOUND);
        }
        removeCodes(Collections.singletonList(code));
    }

    /**
     * 批量修改状态，返回实际处理数量
     */
    public int batchUpdateStatus(List<Long> ids, Integer status) {
        checkStatus(status);
        List<ProductCode> codes = productCodeMapper.selectBatchIds(ids);
        if (codes.isEmpty()) {
            throw new BusinessException(ResultCode.CODE_NOT_FOUND);
        }
        changeStatus(codes, status);
        return codes.size();
    }

    /**
     * 批量删除：先查全部记录拿到图片路径，统一删文件，清缓存，再批量逻辑删除
     */
    @Transactional(rollbackFor = Exception.class)
    public int batchDelete(List<Long> ids) {
        List<ProductCode> codes = productCodeMapper.selectBatchIds(ids);
        if (codes.isEmpty()) {
            throw new BusinessException(ResultCode.CODE_NOT_FOUND);
        }
        removeCodes(codes);
        return codes.size();
    }

    private void changeStatus(List<ProductCode> codes, Integer status) {
        List<Long> ids = codes.stream().map(ProductCode::getId).toList();
        productCodeMapper.update(null, new LambdaUpdateWrapper<ProductCode>()
                .in(ProductCode::getId, ids)
                .set(ProductCode::getStatus, status));
        codes.forEach(c -> evictMeta(c.getCode()));
    }

    /**
     * 删除流程：
     * 1. 先拿图片 URL 还原 storageKey，调 StorageStrategy.deleteImage 物理删除
     * 2. 清 Redis 元数据缓存
     * 3. MyBatis-Plus 逻辑删除 product_code 记录
     */
    private void removeCodes(List<ProductCode> codes) {
        var storage = mediaStrategyFactory.getStorageStrategy();
        // 当前生效的对外前缀（本地 /upload 或 OSS 域名），用于从 URL 反推 storageKey
        var settings = mediaStrategyFactory.settings();
        String urlPrefix = settings.isOss() ? settings.effectiveOssPrefix() : settings.effectiveLocalPrefix();

        for (ProductCode code : codes) {
            if (StrUtil.isNotBlank(code.getQrImageUrl())) {
                try {
                    String storageKey = extractStorageKey(code.getQrImageUrl(), urlPrefix);
                    if (StrUtil.isNotBlank(storageKey)) {
                        boolean deleted = storage.deleteImage(storageKey);
                        if (deleted) {
                            log.info("已删除二维码图片 storageKey={}", storageKey);
                        } else {
                            log.info("二维码图片不存在或删除失败 storageKey={}（可能是历史脏数据）", storageKey);
                        }
                    }
                } catch (Exception e) {
                    log.warn("删除二维码图片异常 code={}: {}", code.getCode(), e.getMessage());
                }
            }
            evictMeta(code.getCode());
        }
        List<Long> ids = codes.stream().map(ProductCode::getId).toList();
        productCodeMapper.deleteBatchIds(ids);
    }

    /**
     * 从 fileUrl 里还原 storageKey（相对于 rootPath 的相对路径）。
     * LocalStorageStrategy 的约定：fileUrl = urlPrefix + "/" + storageKey
     * 例：urlPrefix="http://host:8082/upload"，fileUrl="http://host:8082/upload/qrcode/images/2026/10/23/xxx.png"
     *      → storageKey = "qrcode/images/2026/10/23/xxx.png"
     */
    private String extractStorageKey(String fileUrl, String urlPrefix) {
        if (StrUtil.isBlank(fileUrl)) return null;

        // 标准情况：剥掉 urlPrefix + "/"
        if (StrUtil.isNotBlank(urlPrefix) && fileUrl.startsWith(urlPrefix)) {
            String tail = fileUrl.substring(urlPrefix.length());
            if (tail.startsWith("/")) tail = tail.substring(1);
            return tail.isEmpty() ? null : tail;
        }

        // 历史遗留：/api/media/xxx.png（media 根目录下直接放，相对路径就是 xxx.png）
        if (fileUrl.startsWith("/api/media/")) {
            return fileUrl.substring("/api/media/".length());
        }

        // 历史遗留：/upload/xxx.png
        if (fileUrl.startsWith("/upload/")) {
            return fileUrl.substring("/upload/".length());
        }

        // 兜底：完整 http URL 但 urlPrefix 没配，从第 3 个 "/" 开始取（http://host/xxx → xxx）
        if (fileUrl.startsWith("http")) {
            int idx1 = fileUrl.indexOf("//");
            int idx3 = fileUrl.indexOf('/', idx1 + 2);
            if (idx3 > 0) {
                String tail = fileUrl.substring(idx3 + 1);
                return tail.isEmpty() ? null : tail;
            }
        }
        return null;
    }

    private void checkStatus(Integer status) {
        if (status == null || (status != 0 && status != 1)) {
            throw new BusinessException("状态只能为 1（有效）或 0（作废）");
        }
    }

    private void evictMeta(String code) {
        redisTemplate.delete(META_KEY_PREFIX + code);
    }

    /**
     * 生成普通产品码（同产品复用一张）：
     * - 先清理该产品已存在的 TYPE_NORMAL 旧码（含磁盘图片 + Redis 缓存 + 逻辑删除）
     * - Hutool QrCodeUtil 生成普通二维码，内容为 H5 短链：scan-base-url + "/" + code
     * - 保存到本地 upload/qrcode/
     * - 插入 product_code，type = 1
     */
    @Transactional(rollbackFor = Exception.class)
    public NormalCodeVO generateNormalCode(Long productId) {
        Product product = productMapper.selectById(productId);
        if (product == null) {
            throw new BusinessException(ResultCode.NOT_FOUND.getCode(), "产品不存在");
        }

        // 1. 清理旧的普通码（本产品、TYPE_NORMAL）
        List<ProductCode> oldList = productCodeMapper.selectList(
                new LambdaQueryWrapper<ProductCode>()
                        .eq(ProductCode::getProductId, productId)
                        .eq(ProductCode::getCodeType, ProductCode.TYPE_NORMAL));
        if (!oldList.isEmpty()) {
            removeCodes(oldList);
            log.info("已清理产品 {} 的 {} 张旧普通码", productId, oldList.size());
        }

        // 2. 生成普通二维码（Hutool）
        String code = codeGenerator.next();
        String content = buildScanContent(code);
        byte[] qrBytes;
        try {
            qrBytes = QrCodeUtil.generatePng(content, 320, 320);
        } catch (Exception e) {
            log.error("Hutool 生成普通二维码失败 code={}", code, e);
            throw new BusinessException("生成普通二维码失败: " + e.getMessage());
        }

        // 3. 存到本地 upload/qrcode/
        MediaUploadResult upload = mediaStrategyFactory.getStorageStrategy()
                .uploadImageBytes(qrBytes, code + ".png", "qrcode");

        // 4. 插入 product_code
        ProductCode entity = new ProductCode();
        entity.setProductId(productId);
        entity.setCode(code);
        entity.setCodeType(ProductCode.TYPE_NORMAL);
        entity.setQrImageUrl(upload.getFileUrl());
        entity.setScanLimit(0); // 普通码不限次数
        entity.setScanCount(0);
        entity.setStatus(1);
        productCodeMapper.insert(entity);

        log.info("普通产品码已生成: productId={}, code={}, qr={}", productId, code, upload.getFileUrl());

        return NormalCodeVO.builder()
                .productId(productId)
                .productName(product.getProductName())
                .code(code)
                .qrImageUrl(upload.getFileUrl())
                .scanLimit(entity.getScanLimit())
                .mock(false)
                .build();
    }

    /**
     * 启动一物一码批量生成（异步）
     */
    public BatchTaskProgress startBatch(BatchGenerateDTO dto) {
        Product product = productMapper.selectById(dto.getProductId());
        if (product == null) {
            throw new BusinessException(ResultCode.NOT_FOUND.getCode(), "产品不存在");
        }

        String batchNo = StrUtil.isNotBlank(dto.getBatchNo())
                ? dto.getBatchNo()
                : "B" + LocalDateTime.now().format(BATCH_NO_FMT);

        // scanLimit：前端传 → 用；前端没传 → 用配置 default-scan-limit
        int scanLimit = dto.getScanLimit() != null ? dto.getScanLimit() : this.defaultScanLimit;

        BatchTaskProgress progress = new BatchTaskProgress();
        progress.setTaskId(IdUtil.fastSimpleUUID());
        progress.setProductId(product.getId());
        progress.setProductName(product.getProductName());
        progress.setBatchNo(batchNo);
        progress.setTotal(dto.getCount());
        progress.setGenerated(0);
        progress.setScanLimit(scanLimit);
        progress.setStatus(BatchTaskProgress.STATUS_RUNNING);
        progress.setStartTime(LocalDateTime.now());
        progressStore.save(progress);

        // 持久化到 DB（即使 Redis 丢了也能从 batch_code_task 恢复历史）
        BatchCodeTask task = new BatchCodeTask();
        task.setTaskId(progress.getTaskId());
        task.setProductId(product.getId());
        task.setProductName(product.getProductName());
        task.setBatchNo(batchNo);
        task.setTotal(dto.getCount());
        task.setGenerated(0);
        task.setScanLimit(scanLimit);
        task.setStatus(BatchTaskProgress.STATUS_RUNNING);
        task.setStartTime(LocalDateTime.now());
        batchCodeTaskMapper.insert(task);

        batchCodeService.execute(progress.getTaskId(), product.getId(),
                product.getProductName(), dto.getCount(), batchNo, scanLimit);
        return progress;
    }

    /**
     * 查询批量生成进度
     */
    public BatchTaskProgress getProgress(String taskId) {
        BatchTaskProgress progress = progressStore.get(taskId);
        if (progress == null) {
            throw new BusinessException(ResultCode.BATCH_TASK_NOT_FOUND);
        }
        return progress;
    }

    /**
     * 为已有二维码（普通码 / 一物一码都支持）补生成图片。
     * 全部用 Hutool QrCodeUtil 生成普通 H5 短链二维码，不再调微信接口。
     */
    public String generateQrImage(Long codeId) {
        ProductCode entity = productCodeMapper.selectById(codeId);
        if (entity == null) {
            throw new BusinessException(ResultCode.CODE_NOT_FOUND);
        }
        if (StrUtil.isNotBlank(entity.getQrImageUrl())) {
            return entity.getQrImageUrl();
        }

        byte[] qrBytes;
        try {
            qrBytes = QrCodeUtil.generatePng(buildScanContent(entity.getCode()), 320, 320);
        } catch (Exception e) {
            log.error("补生成二维码失败 code={}", entity.getCode(), e);
            throw new BusinessException("生成二维码失败: " + e.getMessage());
        }

        MediaUploadResult upload = mediaStrategyFactory.getStorageStrategy()
                .uploadImageBytes(qrBytes, entity.getCode() + ".png", "qrcode");

        entity.setQrImageUrl(upload.getFileUrl());
        productCodeMapper.updateById(entity);
        return upload.getFileUrl();
    }

    /**
     * 组装 H5 短链二维码内容
     */
    public String buildScanContent(String code) {
        if (StrUtil.isBlank(scanBaseUrl)) {
            throw new BusinessException("未配置 trace.h5.scan-base-url，无法生成二维码");
        }
        return scanBaseUrl.endsWith("/") ? scanBaseUrl + code : scanBaseUrl + "/" + code;
    }

    /**
     * 获取 CSV 导出文件
     */
    public BatchCsvFile getCsvFile(String taskId) {
        BatchTaskProgress progress = getProgress(taskId);
        if (!BatchTaskProgress.STATUS_SUCCESS.equals(progress.getStatus())) {
            throw new BusinessException(ResultCode.BATCH_TASK_NOT_DONE);
        }
        File file = batchCodeService.csvFile(taskId);
        if (!file.exists()) {
            throw new BusinessException("导出文件不存在或已过期");
        }
        String fileName = "product_" + progress.getProductId() + "_" + progress.getBatchNo() + ".csv";
        return new BatchCsvFile(file, fileName);
    }
}
