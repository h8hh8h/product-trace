package com.trace.product.controller;

import com.trace.product.common.Result;
import com.trace.product.ratelimit.RateLimit;
import com.trace.product.storage.MediaStrategyFactory;
import com.trace.product.storage.MediaStorageType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 健康检查：服务状态 / 数据库建表校验 / 限流演示
 */
@Slf4j
@RestController
@RequestMapping("/health")
@RequiredArgsConstructor
public class HealthController {

    private final MediaStrategyFactory mediaStrategyFactory;
    private final JdbcTemplate jdbcTemplate;

    private static final List<String> REQUIRED_TABLES =
            List.of("sys_admin", "product", "product_code", "scan_record", "media_file");

    /**
     * 服务健康检查
     */
    @GetMapping
    public Result<Map<String, Object>> health() {
        Map<String, Object> info = new LinkedHashMap<>();
        info.put("status", "UP");
        info.put("service", "product-trace");
        info.put("timestamp", LocalDateTime.now().toString());
        MediaStorageType type = mediaStrategyFactory.currentType();
        info.put("mediaStorageType", type.getCode());
        info.put("imageStrategy", mediaStrategyFactory.getStorageStrategy().getClass().getSimpleName());
        info.put("videoStrategy", mediaStrategyFactory.getVideoStrategy().getClass().getSimpleName());
        return Result.success(info);
    }

    /**
     * 数据库建表校验：逐表检查是否存在
     */
    @GetMapping("/db")
    public Result<Map<String, Object>> dbCheck() {
        Map<String, Object> tables = new LinkedHashMap<>();
        try {
            for (String table : REQUIRED_TABLES) {
                Integer count = jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM information_schema.tables " +
                                "WHERE table_schema = DATABASE() AND table_name = ?",
                        Integer.class, table);
                tables.put(table, (count != null && count > 0) ? "OK" : "MISSING");
            }
        } catch (Exception e) {
            log.error("数据库检查失败", e);
            return Result.fail("数据库连接失败: " + e.getMessage());
        }
        boolean allReady = tables.values().stream().allMatch("OK"::equals);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("allTablesReady", allReady);
        data.put("tables", tables);
        return allReady ? Result.success(data) : Result.fail("部分表缺失，请先执行 deploy/schema.sql");
    }

    /**
     * 限流演示：同一 IP 60 秒内最多 3 次
     */
    @GetMapping("/rate-limit-demo")
    @RateLimit(limit = 3, time = 60)
    public Result<String> rateLimitDemo() {
        return Result.success("请求成功，当前时间: " + LocalDateTime.now());
    }
}
