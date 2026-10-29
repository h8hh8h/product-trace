-- ============================================================
-- v3 安全加固与性能优化 SQL 迁移脚本
-- 幂等：可重复执行，已存在的对象会自动跳过
-- 执行：mysql -uroot -p product_trace < migration-v3.sql
-- ============================================================

-- ------------------------------------------------------------
-- 1) scan_record 补齐缺失索引
--    已有：idx_code_id / idx_code_scan_time(code,scan_time) / idx_scan_time / idx_is_fake
--    补：product_id（按产品统计扫码）、scan_result（按结果类型统计）、scan_ip（IP 画像）
-- ------------------------------------------------------------
SET @idx = (SELECT COUNT(*) FROM information_schema.STATISTICS
            WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'scan_record' AND INDEX_NAME = 'idx_product_id');
SET @ddl = IF(@idx = 0, 'ALTER TABLE scan_record ADD INDEX idx_product_id (product_id)', 'SELECT 1');
PREPARE s FROM @ddl; EXECUTE s; DEALLOCATE PREPARE s;

SET @idx = (SELECT COUNT(*) FROM information_schema.STATISTICS
            WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'scan_record' AND INDEX_NAME = 'idx_scan_result');
SET @ddl = IF(@idx = 0, 'ALTER TABLE scan_record ADD INDEX idx_scan_result (scan_result)', 'SELECT 1');
PREPARE s FROM @ddl; EXECUTE s; DEALLOCATE PREPARE s;

SET @idx = (SELECT COUNT(*) FROM information_schema.STATISTICS
            WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'scan_record' AND INDEX_NAME = 'idx_scan_ip');
SET @ddl = IF(@idx = 0, 'ALTER TABLE scan_record ADD INDEX idx_scan_ip (scan_ip)', 'SELECT 1');
PREPARE s FROM @ddl; EXECUTE s; DEALLOCATE PREPARE s;

-- ------------------------------------------------------------
-- 2) product_code 补 (product_id, id) 复合索引
--    用于后台按产品筛选 + 分页 ORDER BY id DESC 的延迟关联优化
-- ------------------------------------------------------------
SET @idx = (SELECT COUNT(*) FROM information_schema.STATISTICS
            WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'product_code' AND INDEX_NAME = 'idx_product_id_id');
SET @ddl = IF(@idx = 0, 'ALTER TABLE product_code ADD INDEX idx_product_id_id (product_id, id)', 'SELECT 1');
PREPARE s FROM @ddl; EXECUTE s; DEALLOCATE PREPARE s;

-- ------------------------------------------------------------
-- 3) sys_admin 增加密码策略字段
--    must_change_password = 1 时后端只放行 /admin/auth/**，强制先改密码
-- ------------------------------------------------------------
SET @col = (SELECT COUNT(*) FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_admin'
              AND COLUMN_NAME = 'must_change_password');
SET @ddl = IF(@col = 0,
  'ALTER TABLE sys_admin ADD COLUMN must_change_password TINYINT NOT NULL DEFAULT 0 COMMENT ''1-必须先修改初始密码'' AFTER status',
  'SELECT 1');
PREPARE s FROM @ddl; EXECUTE s; DEALLOCATE PREPARE s;

SET @col = (SELECT COUNT(*) FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_admin'
              AND COLUMN_NAME = 'password_update_time');
SET @ddl = IF(@col = 0,
  'ALTER TABLE sys_admin ADD COLUMN password_update_time DATETIME NULL COMMENT ''最后一次修改密码时间（用于 90 天强制更换）'' AFTER must_change_password',
  'SELECT 1');
PREPARE s FROM @ddl; EXECUTE s; DEALLOCATE PREPARE s;

-- ------------------------------------------------------------
-- 4) 把当前仍在用初始密码的 admin 账号标记为「必须改密」
--    如果已经改过密码，执行下面这条 UPDATE 前请先把它注释掉
-- ------------------------------------------------------------
UPDATE sys_admin SET must_change_password = 1 WHERE username = 'admin' AND must_change_password = 0;

-- ------------------------------------------------------------
-- 5) 校验输出（人工确认用）
-- ------------------------------------------------------------
SELECT '=== scan_record 索引 ===' AS section;
SELECT INDEX_NAME, GROUP_CONCAT(COLUMN_NAME ORDER BY SEQ_IN_INDEX) AS columns_
FROM information_schema.STATISTICS
WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'scan_record'
GROUP BY INDEX_NAME;

SELECT '=== sys_admin 密码策略字段 ===' AS section;
SELECT COLUMN_NAME, COLUMN_TYPE, COLUMN_DEFAULT, COLUMN_COMMENT
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_admin'
  AND COLUMN_NAME IN ('must_change_password', 'password_update_time');

SELECT '=== 需强制改密的账号 ===' AS section;
SELECT id, username, must_change_password FROM sys_admin;
