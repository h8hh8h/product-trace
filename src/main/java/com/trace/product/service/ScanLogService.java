package com.trace.product.service;

import com.trace.product.entity.ScanRecord;
import com.trace.product.mapper.ProductCodeMapper;
import com.trace.product.mapper.ScanRecordMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * 扫码日志异步落库
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ScanLogService {

    private final ScanRecordMapper scanRecordMapper;
    private final ProductCodeMapper productCodeMapper;
    private final IpRegionService ipRegionService;

    @Async("taskExecutor")
    public void saveLog(ScanRecord record) {
        try {
            // 离线 IP 库解析归属地，写入记录（异步线程内执行，不影响扫码响应）
            IpRegionService.GeoInfo geo = ipRegionService.resolve(record.getScanIp());
            if (geo != null) {
                record.setScanCountry(geo.country());
                record.setScanProvince(geo.province());
                record.setScanCity(geo.city());
                record.setScanLat(geo.lat());
                record.setScanLng(geo.lng());
            }
            scanRecordMapper.insert(record);
        } catch (Exception e) {
            log.error("扫码日志写入失败: code={}", record.getCode(), e);
        }
    }

    @Async("taskExecutor")
    public void increaseScanCount(Long codeId) {
        if (codeId == null) {
            return;
        }
        try {
            productCodeMapper.increaseScanCount(codeId);
        } catch (Exception e) {
            log.error("扫码次数更新失败: codeId={}", codeId, e);
        }
    }

    /** 作废状态下被扫码次数 +1 */
    @Async("taskExecutor")
    public void increaseVoidScanCount(Long codeId) {
        if (codeId == null) return;
        try {
            productCodeMapper.increaseVoidScanCount(codeId);
        } catch (Exception e) {
            log.error("作废扫码次数更新失败: codeId={}", codeId, e);
        }
    }

    /** 超限后继续被扫码次数 +1 */
    @Async("taskExecutor")
    public void increaseOverLimitScanCount(Long codeId) {
        if (codeId == null) return;
        try {
            productCodeMapper.increaseOverLimitScanCount(codeId);
        } catch (Exception e) {
            log.error("超限续扫次数更新失败: codeId={}", codeId, e);
        }
    }
}
