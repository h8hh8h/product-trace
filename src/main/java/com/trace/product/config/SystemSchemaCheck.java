package com.trace.product.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 启动自检：校验 RBAC / 审计相关表的字段与数据库账号权限
 * <p>
 * 背景：生产迁移脚本曾因 information_schema 权限不足（#1044）中断，表由人工补建，
 * 容易出现「列缺失」「建在了别的库」「应用账号没有新表权限」三类问题。
 * 这里在启动时用应用自身的账号逐表逐列做一次 SELECT，把真实报错写进日志，
 * 便于直接定位，而不是只在接口里返回「系统繁忙」。
 * <p>
 * 自检失败不会阻止启动（避免影响线上可用性），只在日志里输出 ERROR 与修复建议。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SystemSchemaCheck implements ApplicationRunner {

    private final JdbcTemplate jdbcTemplate;

    private record TableSpec(String table, List<String> columns) {
    }

    private static final List<TableSpec> SPECS = List.of(
            new TableSpec("sys_role", List.of(
                    "id", "role_code", "role_name", "description", "permissions",
                    "status", "create_time", "update_time")),
            new TableSpec("sys_admin", List.of(
                    "id", "username", "password", "nickname", "avatar", "role", "role_id",
                    "status", "must_change_password", "password_update_time",
                    "last_login_time", "last_login_ip", "deleted", "create_time", "update_time")),
            new TableSpec("admin_operation_log", List.of(
                    "id", "admin_id", "admin_username", "module", "action", "method",
                    "request_uri", "http_method", "target_id", "request_params",
                    "response_code", "error_msg", "ip", "cost_ms", "created_at")),
            new TableSpec("sys_storage_config", List.of(
                    "id", "storage_type", "local_root_path", "local_url_prefix",
                    "oss_access_key_id", "oss_access_key_secret", "oss_bucket_name",
                    "oss_endpoint", "oss_url_prefix", "update_time"))
    );

    @Override
    public void run(ApplicationArguments args) {
        log.info("[启动自检] 开始校验 RBAC/审计表结构（本类存在即说明已部署 v1.3.0-fix2 及之后的 jar）");
        String schema = "(未知)";
        try {
            schema = jdbcTemplate.queryForObject("SELECT DATABASE()", String.class);
            log.info("[启动自检] 应用实际连接的库：{}", schema);
        } catch (DataAccessException e) {
            log.warn("[启动自检] 无法获取当前库名：{}", rootMessage(e));
        }

        for (TableSpec spec : SPECS) {
            checkTable(schema, spec);
        }
    }

    private void checkTable(String schema, TableSpec spec) {
        String sql = "SELECT " + String.join(", ", spec.columns()) + " FROM " + spec.table() + " LIMIT 1";
        try {
            jdbcTemplate.queryForList(sql);
            log.info("[启动自检] {}.{} 全部 {} 个字段可读，权限正常", schema, spec.table(), spec.columns().size());
            return;
        } catch (DataAccessException e) {
            log.error("[启动自检] 表 {} 整表查询失败：{}", spec.table(), rootMessage(e));
        }

        // 整表失败时逐列定位，区分「列缺失」与「权限不足」
        List<String> missing = new ArrayList<>();
        List<String> denied = new ArrayList<>();
        for (String col : spec.columns()) {
            try {
                jdbcTemplate.queryForList("SELECT " + col + " FROM " + spec.table() + " LIMIT 1");
            } catch (DataAccessException e) {
                String msg = rootMessage(e);
                if (msg.toLowerCase(Locale.ROOT).contains("unknown column")) {
                    missing.add(col);
                } else if (msg.toLowerCase(Locale.ROOT).contains("denied")
                        || msg.contains("1142") || msg.contains("1143")) {
                    denied.add(col);
                }
                log.error("[启动自检] {}.{} 不可用：{}", spec.table(), col, msg);
            }
        }

        if (!missing.isEmpty()) {
            log.error("[启动自检] >>> 表 {} 缺少字段：{}；请执行 deploy/migration-v6-fix.sql，"
                    + "或手工 ALTER TABLE `{}` 补充这些列", spec.table(), missing, spec.table());
        }
        if (!denied.isEmpty()) {
            log.error("[启动自检] >>> 数据库账号缺少表 {} 的访问权限；请用 root 执行："
                    + "GRANT SELECT, INSERT, UPDATE, DELETE ON {}.{} TO '<应用账号>'@'<host>'; FLUSH PRIVILEGES;",
                    spec.table(), schema, spec.table());
        }
        if (missing.isEmpty() && denied.isEmpty()) {
            log.error("[启动自检] >>> 表 {} 的字段与权限均正常，但仍查询失败，请查看上方原始错误"
                    + "（常见原因：表建在了别的库、表名大小写、表不存在）", spec.table());
        }
    }

    private String rootMessage(Throwable e) {
        Throwable t = e;
        while (t.getCause() != null) {
            t = t.getCause();
        }
        return t.getMessage();
    }
}
