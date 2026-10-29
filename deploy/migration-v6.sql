-- ============================================================
-- v6 RBAC 角色权限
-- 幂等：可重复执行
-- 执行：mysql -uroot -p product_trace < migration-v6.sql
-- ============================================================

-- 1) 角色表
CREATE TABLE IF NOT EXISTS sys_role (
  id           BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  role_code    VARCHAR(64)  NOT NULL COMMENT '角色编码（唯一）',
  role_name    VARCHAR(64)  NOT NULL COMMENT '角色名称',
  description  VARCHAR(255)          COMMENT '描述',
  permissions  TEXT                  COMMENT 'JSON数组，模块key',
  status       TINYINT      NOT NULL DEFAULT 1 COMMENT '状态：1-启用 0-禁用',
  create_time  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  update_time  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_role_code (role_code)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='后台角色';

-- 2) sys_admin 增加 role_id（NULL 为超级管理员）
SET @col = (SELECT COUNT(*) FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_admin' AND COLUMN_NAME = 'role_id');
SET @ddl = IF(@col = 0,
  'ALTER TABLE sys_admin ADD COLUMN role_id BIGINT NULL COMMENT ''角色ID，NULL为超级管理员'' AFTER role',
  'SELECT 1');
PREPARE s FROM @ddl; EXECUTE s; DEALLOCATE PREPARE s;

-- 3) 校验
SELECT '=== sys_role 表结构 ===' AS section;
SELECT COLUMN_NAME, COLUMN_TYPE, IS_NULLABLE, COLUMN_DEFAULT, COLUMN_COMMENT
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_role'
ORDER BY ORDINAL_POSITION;

SELECT '=== sys_admin.role_id ===' AS section;
SELECT COLUMN_NAME, COLUMN_TYPE, IS_NULLABLE, COLUMN_COMMENT
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_admin' AND COLUMN_NAME = 'role_id';
