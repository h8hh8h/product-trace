package com.trace.product.service;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.trace.product.common.BusinessException;
import com.trace.product.common.ResultCode;
import com.trace.product.dto.CodeMeta;
import com.trace.product.dto.VerifyResultVO;
import com.trace.product.entity.Product;
import com.trace.product.entity.ProductCode;
import com.trace.product.entity.ScanRecord;
import com.trace.product.mapper.ProductCodeMapper;
import com.trace.product.mapper.ProductMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

/**
 * 防伪验证 v2
 * <p>
 * 扫码结果枚举（ScanRecord.scanResult）：
 *   1 = 正常扫码（码有效 + 未超限）
 *   2 = 首次触发超限（当前扫码把 scan_count 从 <=limit 推到 >limit）
 *   3 = 无效码（码不存在 / 被逻辑删 / 产品下架）
 *   4 = 作废码扫码（product_code.status=0 时被扫）
 *   5 = 超限后继续扫（scan_count 已 > limit 后再次扫码）
 * <p>
 * 流程：Redis 缓存码信息 → Lua 原子累加扫码次数 →
 *      根据当前状态分五类 → 异步写 ScanRecord + 更新对应计数。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class VerifyService {

    private static final String META_KEY_PREFIX = "trace:code:meta:";
    private static final String SCAN_KEY_PREFIX = "trace:code:scan:";

    private final ProductCodeMapper productCodeMapper;
    private final ProductMapper productMapper;
    private final RedisTemplate<String, Object> redisTemplate;
    private final StringRedisTemplate stringRedisTemplate;
    private final DefaultRedisScript<List> scanCountScript;
    private final ScanLogService scanLogService;

    @Value("${trace.anti-fake.default-scan-limit:3}")
    private int defaultScanLimit;

    @Value("${trace.anti-fake.meta-ttl:600}")
    private long metaTtl;

    @Value("${trace.anti-fake.count-ttl:604800}")
    private long countTtl;

    public VerifyResultVO verify(String code, String ip, String userAgent, int scanSource) {
        CodeMeta meta = loadMeta(code);

        if (meta == null) {
            recordScan(null, code, null, ip, userAgent, scanSource, 3);
            throw new BusinessException(ResultCode.CODE_NOT_FOUND);
        }
        if (meta.getShelfStatus() != null && meta.getShelfStatus() != 1) {
            recordScan(meta, code, meta.getProductId(), ip, userAgent, scanSource, 3);
            throw new BusinessException(ResultCode.PRODUCT_OFFLINE);
        }

        // ========== 作废码单独处理：不再 throw，而是记录 scanResult=4 并返回告警 ==========
        if (meta.getStatus() != null && meta.getStatus() != 1) {
            scanLogService.increaseVoidScanCount(meta.getCodeId());
            recordScan(meta, code, meta.getProductId(), ip, userAgent, scanSource, 4);
            log.warn("作废码被扫: code={}, ip={}", code, ip);
            return VerifyResultVO.builder()
                    .valid(false)
                    .voidWarning(true)
                    .code(code)
                    .productId(meta.getProductId())
                    .productName(meta.getProductName())
                    .coverImage(meta.getCoverImage())
                    .scanCount(meta.getScanCount())
                    .scanLimit(meta.getScanLimit() == null ? defaultScanLimit : meta.getScanLimit())
                    .message("该码已被商家作废，谨防假冒！")
                    .build();
        }

        // ========== 正常码走 Lua 原子累加 ==========
        int limit = meta.getScanLimit() == null ? defaultScanLimit : meta.getScanLimit();
        int initialCount = meta.getScanCount() == null ? 0 : meta.getScanCount();

        List<?> result = stringRedisTemplate.execute(
                scanCountScript,
                Collections.singletonList(SCAN_KEY_PREFIX + code),
                String.valueOf(limit),
                String.valueOf(countTtl),
                String.valueOf(initialCount)
        );

        int current = 0;
        boolean fake = false;
        if (result != null && result.size() >= 2) {
            current = ((Number) result.get(0)).intValue();
            fake = ((Number) result.get(1)).intValue() == 1;
        }

        if (fake) {
            // 区分「首次触发超限」和「超限后继续扫」
            // Lua INCR 后返回 current，所以扫码前计数 = current - 1
            // 如果扫码前已经 > limit → 续扫；否则 → 首次超限
            int countBeforeScan = current - 1;
            if (countBeforeScan > limit) {
                // scanResult=5：已超限后继续扫
                scanLogService.increaseOverLimitScanCount(meta.getCodeId());
                recordScan(meta, code, meta.getProductId(), ip, userAgent, scanSource, 5);
                log.warn("超限后续扫: code={}, ip={}, current={}, limit={}", code, ip, current, limit);
                return VerifyResultVO.builder()
                        .valid(false)
                        .fakeWarning(true)
                        .overLimitContinue(true)
                        .code(code)
                        .productId(meta.getProductId())
                        .productName(meta.getProductName())
                        .coverImage(meta.getCoverImage())
                        .scanCount(current)
                        .scanLimit(limit)
                        .message("严重预警！该码已被扫描 " + current + " 次（上限 " + limit + " 次），属假冒商品")
                        .build();
            } else {
                // scanResult=2：首次触发超限（当前这次扫码是第 limit+1 次）
                scanLogService.increaseScanCount(meta.getCodeId());
                recordScan(meta, code, meta.getProductId(), ip, userAgent, scanSource, 2);
                log.warn("首次超限: code={}, ip={}, current={}, limit={}", code, ip, current, limit);
                return VerifyResultVO.builder()
                        .valid(false)
                        .fakeWarning(true)
                        .code(code)
                        .productId(meta.getProductId())
                        .productName(meta.getProductName())
                        .coverImage(meta.getCoverImage())
                        .scanCount(current)
                        .scanLimit(limit)
                        .message("该码已被扫描 " + current + " 次，超过上限 " + limit + " 次，请谨慎购买")
                        .build();
            }
        }

        // scanResult=1：正常扫码
        recordScan(meta, code, meta.getProductId(), ip, userAgent, scanSource, 1);
        scanLogService.increaseScanCount(meta.getCodeId());

        return VerifyResultVO.builder()
                .valid(true)
                .fakeWarning(false)
                .code(code)
                .productId(meta.getProductId())
                .productName(meta.getProductName())
                .coverImage(meta.getCoverImage())
                .scanCount(current)
                .scanLimit(limit)
                .message(limit > 0
                        ? "正品验证通过，该码第 " + current + " 次被扫描"
                        : "正品验证通过")
                .build();
    }

    /**
     * 读取码缓存，未命中则查库并回填
     */
    private CodeMeta loadMeta(String code) {
        String key = META_KEY_PREFIX + code;
        Object cached = redisTemplate.opsForValue().get(key);
        if (cached instanceof CodeMeta) {
            return (CodeMeta) cached;
        }

        ProductCode productCode = productCodeMapper.selectOne(
                new LambdaQueryWrapper<ProductCode>()
                        .eq(ProductCode::getCode, code)
                        .last("LIMIT 1"));
        if (productCode == null) {
            return null;
        }

        Product product = productMapper.selectById(productCode.getProductId());

        CodeMeta meta = new CodeMeta();
        meta.setCodeId(productCode.getId());
        meta.setCode(productCode.getCode());
        meta.setProductId(productCode.getProductId());
        meta.setScanLimit(productCode.getScanLimit());
        meta.setScanCount(productCode.getScanCount());
        meta.setStatus(productCode.getStatus());
        if (product != null) {
            meta.setProductName(product.getProductName());
            meta.setCoverImage(product.getCoverImage());
            meta.setShelfStatus(product.getShelfStatus());
        }

        redisTemplate.opsForValue().set(key, meta, Duration.ofSeconds(metaTtl));
        return meta;
    }

    private void recordScan(CodeMeta meta, String code, Long productId, String ip,
                            String userAgent, int scanSource, int scanResult) {
        ScanRecord record = new ScanRecord();
        record.setCodeId(meta == null ? null : meta.getCodeId());
        record.setCode(code);
        record.setProductId(productId);
        record.setScanIp(ip);
        record.setScanSource(scanSource);
        record.setUserAgent(StrUtil.maxLength(userAgent, 500));
        record.setScanResult(scanResult);
        record.setScanTime(LocalDateTime.now());
        scanLogService.saveLog(record);
    }
}
