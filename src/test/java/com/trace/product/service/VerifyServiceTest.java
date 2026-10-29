package com.trace.product.service;

import com.trace.product.common.BusinessException;
import com.trace.product.common.ResultCode;
import com.trace.product.dto.CodeMeta;
import com.trace.product.dto.VerifyResultVO;
import com.trace.product.entity.ScanRecord;
import com.trace.product.mapper.ProductCodeMapper;
import com.trace.product.mapper.ProductMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * VerifyService 防伪验证五类分支单测：全部 mock，离线可跑。
 * scanResult 分类：1 正常 / 2 首次超限 / 3 无效码 / 4 作废码 / 5 超限续扫
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class VerifyServiceTest {

    @Mock
    private ProductCodeMapper productCodeMapper;
    @Mock
    private ProductMapper productMapper;
    @Mock
    private RedisTemplate<String, Object> redisTemplate;
    @Mock
    private StringRedisTemplate stringRedisTemplate;
    @Mock
    private DefaultRedisScript<List> scanCountScript;
    @Mock
    private ScanLogService scanLogService;
    @Mock
    private ValueOperations<String, Object> valueOps;

    private VerifyService verifyService;

    @BeforeEach
    void setUp() {
        verifyService = new VerifyService(productCodeMapper, productMapper,
                redisTemplate, stringRedisTemplate, scanCountScript, scanLogService);
        ReflectionTestUtils.setField(verifyService, "defaultScanLimit", 3);
        ReflectionTestUtils.setField(verifyService, "metaTtl", 600L);
        ReflectionTestUtils.setField(verifyService, "countTtl", 604800L);
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
    }

    /** 构造一个「有效、上架、未超限」的正常码缓存（走 Redis 命中分支，不查库） */
    private CodeMeta normalMeta() {
        CodeMeta meta = new CodeMeta();
        meta.setCodeId(10L);
        meta.setCode("CODE1");
        meta.setProductId(100L);
        meta.setScanLimit(3);
        meta.setScanCount(0);
        meta.setStatus(1);
        meta.setProductName("测试产品");
        meta.setShelfStatus(1);
        return meta;
    }

    private void cacheHit(CodeMeta meta) {
        when(valueOps.get("trace:code:meta:" + meta.getCode())).thenReturn(meta);
    }

    private int capturedScanResult() {
        ArgumentCaptor<ScanRecord> captor = ArgumentCaptor.forClass(ScanRecord.class);
        verify(scanLogService).saveLog(captor.capture());
        return captor.getValue().getScanResult();
    }

    @Test
    void codeNotFoundThrowsAndRecordsInvalidScan() {
        // 缓存未命中且库中不存在
        when(valueOps.get("trace:code:meta:NOPE")).thenReturn(null);
        when(productCodeMapper.selectOne(any())).thenReturn(null);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> verifyService.verify("NOPE", "1.2.3.4", "ua", 1));
        assertEquals(ResultCode.CODE_NOT_FOUND.getCode(), ex.getCode());
        assertEquals(3, capturedScanResult());
    }

    @Test
    void offlineProductThrowsAndRecordsInvalidScan() {
        CodeMeta meta = normalMeta();
        meta.setShelfStatus(0); // 产品下架
        cacheHit(meta);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> verifyService.verify("CODE1", "1.2.3.4", "ua", 1));
        assertEquals(ResultCode.PRODUCT_OFFLINE.getCode(), ex.getCode());
        assertEquals(3, capturedScanResult());
    }

    @Test
    void voidCodeReturnsVoidWarning() {
        CodeMeta meta = normalMeta();
        meta.setStatus(0); // 已作废
        cacheHit(meta);

        VerifyResultVO vo = verifyService.verify("CODE1", "1.2.3.4", "ua", 1);

        assertEquals(Boolean.FALSE, vo.getValid());
        assertEquals(Boolean.TRUE, vo.getVoidWarning());
        assertTrue(vo.getMessage().contains("作废"));
        verify(scanLogService).increaseVoidScanCount(10L);
        assertEquals(4, capturedScanResult());
    }

    @Test
    void normalScanPasses() {
        cacheHit(normalMeta());
        // Lua 返回：当前次数=1，未超限
        when(stringRedisTemplate.execute(eq(scanCountScript), anyList(), any(), any(), any()))
                .thenReturn(List.of(1L, 0L));

        VerifyResultVO vo = verifyService.verify("CODE1", "1.2.3.4", "ua", 1);

        assertEquals(Boolean.TRUE, vo.getValid());
        assertEquals(Boolean.FALSE, vo.getFakeWarning());
        assertEquals(1, vo.getScanCount());
        assertEquals(3, vo.getScanLimit());
        verify(scanLogService).increaseScanCount(10L);
        assertEquals(1, capturedScanResult());
    }

    @Test
    void firstOverLimitScanTriggersFakeWarning() {
        cacheHit(normalMeta());
        // limit=3，本次是第 4 次扫码（扫码前=3，未超过 limit）→ 首次超限
        when(stringRedisTemplate.execute(eq(scanCountScript), anyList(), any(), any(), any()))
                .thenReturn(List.of(4L, 1L));

        VerifyResultVO vo = verifyService.verify("CODE1", "1.2.3.4", "ua", 1);

        assertEquals(Boolean.FALSE, vo.getValid());
        assertEquals(Boolean.TRUE, vo.getFakeWarning());
        assertNotEquals(Boolean.TRUE, vo.getOverLimitContinue());
        assertEquals(4, vo.getScanCount());
        verify(scanLogService).increaseScanCount(10L);
        assertEquals(2, capturedScanResult());
    }

    @Test
    void continuedOverLimitScanTriggersSevereWarning() {
        cacheHit(normalMeta());
        // limit=3，本次是第 5 次扫码（扫码前=4 > limit）→ 超限续扫
        when(stringRedisTemplate.execute(eq(scanCountScript), anyList(), any(), any(), any()))
                .thenReturn(List.of(5L, 1L));

        VerifyResultVO vo = verifyService.verify("CODE1", "1.2.3.4", "ua", 1);

        assertEquals(Boolean.FALSE, vo.getValid());
        assertEquals(Boolean.TRUE, vo.getFakeWarning());
        assertEquals(Boolean.TRUE, vo.getOverLimitContinue());
        assertTrue(vo.getMessage().contains("严重预警"));
        verify(scanLogService).increaseOverLimitScanCount(10L);
        verify(scanLogService, never()).increaseScanCount(anyLong());
        assertEquals(5, capturedScanResult());
    }
}
