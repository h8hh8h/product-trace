-- ============================================================
-- v5 扫码地理位置
-- 幂等：可重复执行
-- 执行：mysql -uroot -p product_trace < migration-v5.sql
-- ============================================================

-- 1) scan_record 增加地理字段（旧数据为 NULL，统计时归入「未知」）
SET @col = (SELECT COUNT(*) FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'scan_record' AND COLUMN_NAME = 'scan_country');
SET @ddl = IF(@col = 0,
  'ALTER TABLE scan_record ADD COLUMN scan_country VARCHAR(32) NULL COMMENT ''国家，如 中国'' AFTER scan_ip',
  'SELECT 1');
PREPARE s FROM @ddl; EXECUTE s; DEALLOCATE PREPARE s;

SET @col = (SELECT COUNT(*) FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'scan_record' AND COLUMN_NAME = 'scan_province');
SET @ddl = IF(@col = 0,
  'ALTER TABLE scan_record ADD COLUMN scan_province VARCHAR(32) NULL COMMENT ''省份，如 广东省'' AFTER scan_country',
  'SELECT 1');
PREPARE s FROM @ddl; EXECUTE s; DEALLOCATE PREPARE s;

SET @col = (SELECT COUNT(*) FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'scan_record' AND COLUMN_NAME = 'scan_city');
SET @ddl = IF(@col = 0,
  'ALTER TABLE scan_record ADD COLUMN scan_city VARCHAR(32) NULL COMMENT ''城市，如 深圳市'' AFTER scan_province',
  'SELECT 1');
PREPARE s FROM @ddl; EXECUTE s; DEALLOCATE PREPARE s;

SET @col = (SELECT COUNT(*) FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'scan_record' AND COLUMN_NAME = 'scan_lat');
SET @ddl = IF(@col = 0,
  'ALTER TABLE scan_record ADD COLUMN scan_lat DECIMAL(10,6) NULL COMMENT ''纬度（IP 库城市中心点近似值）'' AFTER scan_city',
  'SELECT 1');
PREPARE s FROM @ddl; EXECUTE s; DEALLOCATE PREPARE s;

SET @col = (SELECT COUNT(*) FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'scan_record' AND COLUMN_NAME = 'scan_lng');
SET @ddl = IF(@col = 0,
  'ALTER TABLE scan_record ADD COLUMN scan_lng DECIMAL(10,6) NULL COMMENT ''经度（IP 库城市中心点近似值）'' AFTER scan_lat',
  'SELECT 1');
PREPARE s FROM @ddl; EXECUTE s; DEALLOCATE PREPARE s;

-- 2) 省份聚合索引
SET @idx = (SELECT COUNT(*) FROM information_schema.STATISTICS
            WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'scan_record' AND INDEX_NAME = 'idx_scan_province');
SET @ddl = IF(@idx = 0, 'ALTER TABLE scan_record ADD INDEX idx_scan_province (scan_province)', 'SELECT 1');
PREPARE s FROM @ddl; EXECUTE s; DEALLOCATE PREPARE s;

-- 3) 校验
SELECT '=== scan_record 地理字段 ===' AS section;
SELECT COLUMN_NAME, COLUMN_TYPE, COLUMN_COMMENT
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'scan_record'
  AND COLUMN_NAME LIKE 'scan\_%';
