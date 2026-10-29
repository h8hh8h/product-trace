-- ============================================================
-- v2 功能优化 SQL 迁移脚本
-- 执行时机：升级部署前在生产库上跑（在事务里逐块执行）
-- 前置条件：v1 表结构已存在
-- ============================================================

-- 1) product_code：新增「作废状态下被扫次数」和「超限后继续被扫次数」（幂等：先查再改）
SET @col_check = (SELECT COUNT(*) FROM information_schema.COLUMNS
                  WHERE TABLE_SCHEMA = DATABASE()
                    AND TABLE_NAME = 'product_code'
                    AND COLUMN_NAME = 'void_scan_count');
SET @ddl = IF(@col_check = 0,
  'ALTER TABLE product_code ADD COLUMN void_scan_count INT NOT NULL DEFAULT 0 COMMENT ''作废状态下被扫码次数'' AFTER scan_count',
  'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @col_check = (SELECT COUNT(*) FROM information_schema.COLUMNS
                  WHERE TABLE_SCHEMA = DATABASE()
                    AND TABLE_NAME = 'product_code'
                    AND COLUMN_NAME = 'over_limit_scan_count');
SET @ddl = IF(@col_check = 0,
  'ALTER TABLE product_code ADD COLUMN over_limit_scan_count INT NOT NULL DEFAULT 0 COMMENT ''超限后继续被扫码次数'' AFTER void_scan_count',
  'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 2) scan_record：scan_result 语义扩展（老值保持不变，加两个新值）
--    原 1=正常 2=超限/假货 3=无效码（码不存在/被逻辑删）
--    新增 4=作废码扫码（码在 product_code.status=0 时被扫）
--    新增 5=超限后继续扫（scan_limit>0 且 scan_count>scan_limit 时再次扫）
--    无需 ALTER TABLE，只改 Java 侧枚举语义即可

-- 3) 新建「批量生成任务」持久化表（原来只存 Redis TTL 24h，现在 DB 永久留存）
CREATE TABLE IF NOT EXISTS batch_code_task (
  id             BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  task_id        VARCHAR(64)  NOT NULL COMMENT '异步任务 ID（Redis key 用）',
  product_id     BIGINT       NOT NULL COMMENT '产品 ID',
  product_name   VARCHAR(200)          COMMENT '产品名称（冗余快照）',
  batch_no       VARCHAR(64)  NOT NULL COMMENT '批次号',
  total          INT          NOT NULL COMMENT '计划生成总数',
  `generated`    INT          NOT NULL DEFAULT 0 COMMENT '实际生成数',
  scan_limit     INT          NOT NULL DEFAULT 3 COMMENT '本次批次扫码次数上限',
  status         VARCHAR(16)  NOT NULL DEFAULT 'RUNNING' COMMENT 'RUNNING/SUCCESS/FAILED',
  csv_file_path  VARCHAR(500)          COMMENT 'CSV 物理文件路径',
  download_url   VARCHAR(200)          COMMENT 'CSV 下载接口路径',
  message        VARCHAR(500)          COMMENT '失败原因/备注',
  start_time     DATETIME               COMMENT '任务开始时间',
  end_time       DATETIME               COMMENT '任务结束时间',
  create_time    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_task_id (task_id),
  KEY idx_product_id (product_id),
  KEY idx_batch_no (batch_no),
  KEY idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='一物一码批量生成任务';

-- 4) 可选：清理过期 CSV 文件（仅文件，DB 记录留着；生产可配 crontab 定期跑）
-- DELETE FROM batch_code_task WHERE status='SUCCESS' AND create_time < DATE_SUB(NOW(), INTERVAL 30 DAY);
