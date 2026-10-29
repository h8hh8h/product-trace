-- ============================================================
-- v6-fix 系统管理模块修复：手工补建表后的结构校准
-- 背景：迁移脚本因 #1044（information_schema 权限）未完整执行，
--       手工补建的 sys_role 可能缺列（尤其 create_time/update_time），
--       导致 MyBatis-Plus 全列 SELECT 报 Unknown column。
--
-- 用法（二选一）：
--   方案A：sys_role 还没有正式数据，直接 DROP 重建（最干净，推荐）
--   方案B：sys_role 已有数据要保留，逐条 ALTER 补列
--
-- 注意：本脚本不使用 information_schema + PREPARE（避开 #1044），
--       逐条执行；报 1060（Duplicate column）说明该列已存在，忽略即可；
--       报 1061（Duplicate key）说明索引已存在，忽略即可。
-- ============================================================

-- ============================================================
-- 方案A：sys_role 直接重建（推荐，新表无正式数据时）
-- 执行方案A后请跳过方案B
-- ============================================================

DROP TABLE IF EXISTS sys_role;

CREATE TABLE sys_role (
  id           BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  role_code    VARCHAR(64)  NOT NULL COMMENT '角色编码（唯一）',
  role_name    VARCHAR(64)  NOT NULL COMMENT '角色名称',
  description  VARCHAR(255)          DEFAULT NULL COMMENT '描述',
  permissions  TEXT                  DEFAULT NULL COMMENT 'JSON数组，模块key',
  status       TINYINT      NOT NULL DEFAULT 1 COMMENT '状态：1-启用 0-禁用',
  create_time  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  update_time  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_role_code (role_code)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='后台角色';


-- ============================================================
-- 方案B：保留 sys_role 数据，逐条补列（与方案A二选一）
-- 把下面每行单独执行；报 1060 就跳过该行
-- ============================================================

-- ALTER TABLE sys_role ADD COLUMN role_code   VARCHAR(64)  NOT NULL DEFAULT '' COMMENT '角色编码（唯一）' AFTER id;
-- ALTER TABLE sys_role ADD COLUMN role_name   VARCHAR(64)  NOT NULL DEFAULT '' COMMENT '角色名称' AFTER role_code;
-- ALTER TABLE sys_role ADD COLUMN description VARCHAR(255) DEFAULT NULL COMMENT '描述' AFTER role_name;
-- ALTER TABLE sys_role ADD COLUMN permissions TEXT         DEFAULT NULL COMMENT 'JSON数组，模块key' AFTER description;
-- ALTER TABLE sys_role ADD COLUMN status      TINYINT      NOT NULL DEFAULT 1 COMMENT '状态：1-启用 0-禁用' AFTER permissions;
-- ALTER TABLE sys_role ADD COLUMN create_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间' AFTER status;
-- ALTER TABLE sys_role ADD COLUMN update_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间' AFTER create_time;
-- ALTER TABLE sys_role ADD UNIQUE KEY uk_role_code (role_code);


-- ============================================================
-- sys_admin 补齐（两个方案都要执行）
-- 每行单独执行；报 1060 说明已存在，忽略
-- ============================================================

ALTER TABLE sys_admin ADD COLUMN role_id              BIGINT     DEFAULT NULL COMMENT '角色ID，NULL为超级管理员' AFTER role;
ALTER TABLE sys_admin ADD COLUMN must_change_password TINYINT    NOT NULL DEFAULT 0 COMMENT '1-必须修改初始密码' AFTER status;
ALTER TABLE sys_admin ADD COLUMN password_update_time DATETIME   DEFAULT NULL COMMENT '最后修改密码时间' AFTER must_change_password;
ALTER TABLE sys_admin ADD COLUMN last_login_time      DATETIME   DEFAULT NULL COMMENT '最后登录时间' AFTER password_update_time;
ALTER TABLE sys_admin ADD COLUMN last_login_ip        VARCHAR(64) DEFAULT NULL COMMENT '最后登录IP' AFTER last_login_time;


-- ============================================================
-- 校验：对比下方输出与 Java 实体字段是否一一对应
-- ============================================================
SHOW COLUMNS FROM sys_role;
-- 期望 8 列：id, role_code, role_name, description, permissions, status, create_time, update_time

SHOW COLUMNS FROM sys_admin;
-- 期望包含：id, username, password, nickname, avatar, role, role_id,
--          status, must_change_password, password_update_time,
--          last_login_time, last_login_ip, deleted, create_time, update_time
