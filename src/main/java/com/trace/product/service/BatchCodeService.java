package com.trace.product.service;

import cn.hutool.core.io.FileUtil;
import cn.hutool.extra.qrcode.QrCodeUtil;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.trace.product.common.BusinessException;
import com.trace.product.dto.BatchTaskProgress;
import com.trace.product.entity.BatchCodeTask;
import com.trace.product.entity.ProductCode;
import com.trace.product.mapper.BatchCodeTaskMapper;
import com.trace.product.mapper.ProductCodeMapper;
import com.trace.product.storage.MediaStrategyFactory;
import com.trace.product.storage.MediaUploadResult;
import com.trace.product.util.CodeGenerator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.BufferedWriter;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 一物一码批量生成（异步执行）
 *
 * <p>每次循环：生成唯一 UUID 码值 → Hutool QrCodeUtil 生成 H5 短链二维码图片 → 存 upload/qrcode/ → 写 CSV → insertBatch 入库。
 * 整个循环必须在 try-catch 里，任何异常都要 log.error 并把进度置 FAILED，绝不能静默吞掉。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BatchCodeService {

    private final ProductCodeMapper productCodeMapper;
    private final BatchCodeTaskMapper batchCodeTaskMapper;
    private final CodeGenerator codeGenerator;
    private final BatchProgressStore progressStore;
    private final StorageConfigService storageConfigService;
    private final MediaStrategyFactory mediaStrategyFactory;

    @Value("${trace.batch.chunk-size:500}")
    private int chunkSize;

    @Value("${trace.batch.progress-interval:100}")
    private int progressInterval;

    @Value("${trace.anti-fake.default-scan-limit:3}")
    private int defaultScanLimit;

    @Value("${trace.h5.scan-base-url:}")
    private String scanBaseUrl;

    /**
     * 异步生成一物一码，并同步输出 CSV
     * 所有异常必须显式 log.error 并将进度置 FAILED
     *
     * @param scanLimit 本次批次每个码的扫码次数上限（0=不限）
     */
    @Async("taskExecutor")
    public void execute(String taskId, Long productId, String productName, int count, String batchNo, int scanLimit) {
        BatchTaskProgress progress = progressStore.get(taskId);
        if (progress == null) {
            log.warn("批量任务不存在: {}", taskId);
            return;
        }

        File csvFile = csvFile(taskId);
        try {
            if (scanBaseUrl == null || scanBaseUrl.isBlank()) {
                throw new BusinessException("未配置 trace.h5.scan-base-url，无法生成二维码");
            }

            FileUtil.mkParentDirs(csvFile);
            try (BufferedWriter writer = Files.newBufferedWriter(csvFile.toPath(), StandardCharsets.UTF_8)) {
                // UTF-8 BOM，避免 Excel 打开中文乱码
                writer.write('\uFEFF');
                writer.write("码值,产品ID,产品名称,批次号,扫码上限,H5链接\n");

                List<ProductCode> buffer = new ArrayList<>(chunkSize);
                List<String> samples = new ArrayList<>(5);

                for (int i = 1; i <= count; i++) {
                    String code = codeGenerator.next();

                    // 1. 生成 H5 短链二维码图片（Hutool）
                    String content = scanBaseUrl.endsWith("/") ? scanBaseUrl + code : scanBaseUrl + "/" + code;
                    byte[] qrBytes;
                    try {
                        qrBytes = QrCodeUtil.generatePng(content, 320, 320);
                    } catch (Exception e) {
                        log.error("Hutool 生成二维码失败 taskId={}, code={}, idx={}", taskId, code, i, e);
                        throw new BusinessException("Hutool 生成二维码失败: " + e.getMessage());
                    }

                    MediaUploadResult upload = mediaStrategyFactory.getStorageStrategy()
                            .uploadImageBytes(qrBytes, code + ".png", "qrcode");

                    // 2. 组装入库实体
                    ProductCode entity = new ProductCode();
                    entity.setProductId(productId);
                    entity.setCode(code);
                    entity.setCodeType(ProductCode.TYPE_UNIQUE);
                    entity.setBatchNo(batchNo);
                    entity.setQrImageUrl(upload.getFileUrl());
                    entity.setScanLimit(scanLimit);
                    entity.setScanCount(0);
                    entity.setVoidScanCount(0);
                    entity.setOverLimitScanCount(0);
                    entity.setStatus(1);
                    buffer.add(entity);

                    if (samples.size() < 5) {
                        samples.add(code);
                    }

                    // 3. 写 CSV
                    writer.write(csvLine(code, productId, productName, batchNo, scanLimit));
                    writer.newLine();

                    // 4. 批量入库
                    if (buffer.size() >= chunkSize) {
                        productCodeMapper.insertBatch(buffer);
                        buffer.clear();
                    }

                    // 5. 进度更新（首条 / 每 progress-interval / 末条）
                    if (i == 1 || i % progressInterval == 0 || i == count) {
                        progress.setGenerated(i);
                        progress.setSampleCodes(new ArrayList<>(samples));
                        progressStore.save(progress);
                    }
                }

                // 落库剩余 buffer
                if (!buffer.isEmpty()) {
                    productCodeMapper.insertBatch(buffer);
                }
            }

            progress.setGenerated(count);
            progress.setStatus(BatchTaskProgress.STATUS_SUCCESS);
            progress.setDownloadUrl("/admin/code/batch/" + taskId + "/download");
            progress.setEndTime(LocalDateTime.now());
            progressStore.save(progress);

            // 同步更新 DB 持久化表
            batchCodeTaskMapper.update(null, new LambdaUpdateWrapper<BatchCodeTask>()
                    .eq(BatchCodeTask::getTaskId, taskId)
                    .set(BatchCodeTask::getStatus, BatchTaskProgress.STATUS_SUCCESS)
                    .set(BatchCodeTask::getGenerated, count)
                    .set(BatchCodeTask::getCsvFilePath, csvFile.getAbsolutePath())
                    .set(BatchCodeTask::getDownloadUrl, "/admin/code/batch/" + taskId + "/download")
                    .set(BatchCodeTask::getEndTime, LocalDateTime.now()));

            log.info("一物一码批量生成完成: taskId={}, productId={}, count={}, batchNo={}",
                    taskId, productId, count, batchNo);
        } catch (Exception e) {
            log.error("一物一码批量生成失败: taskId={}, productId={}, count={}", taskId, productId, count, e);
            progress.setStatus(BatchTaskProgress.STATUS_FAILED);
            progress.setMessage("生成失败: " + e.getMessage());
            progress.setEndTime(LocalDateTime.now());
            progressStore.save(progress);
            FileUtil.del(csvFile);

            // 同步更新 DB 持久化表
            batchCodeTaskMapper.update(null, new LambdaUpdateWrapper<BatchCodeTask>()
                    .eq(BatchCodeTask::getTaskId, taskId)
                    .set(BatchCodeTask::getStatus, BatchTaskProgress.STATUS_FAILED)
                    .set(BatchCodeTask::getMessage, "生成失败: " + e.getMessage())
                    .set(BatchCodeTask::getEndTime, LocalDateTime.now()));
        }
    }

    public File csvFile(String taskId) {
        // CSV 始终落在本地存储根目录（后台需直接下载，不入 OSS）
        return new File(storageConfigService.current().getLocalRootPath(), "csv/" + taskId + ".csv");
    }

    private String csvLine(String code, Long productId, String productName, String batchNo, int scanLimit) {
        String link = scanBaseUrl.endsWith("/") ? scanBaseUrl + code : scanBaseUrl + "/" + code;
        return String.join(",",
                quote(code),
                String.valueOf(productId),
                quote(productName),
                quote(batchNo),
                String.valueOf(scanLimit),
                quote(link));
    }

    private String quote(String value) {
        if (value == null) {
            return "\"\"";
        }
        return "\"" + value.replace("\"", "\"\"") + "\"";
    }
}
