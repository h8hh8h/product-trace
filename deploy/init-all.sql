-- ============================================================
-- 产品数字化展示与一物一码溯源系统 - 全量初始化脚本（All-in-One）
-- 版本：v1.3.0 整合版（替代 schema.sql + migration-v2 ~ v6 全部增量脚本）
--
-- 适用环境：MySQL 8.0+ / InnoDB / utf8mb4
-- 特点：
--   1. 全新干净库一步到位，零依赖 information_schema / PREPARE 动态语句
--   2. 全部小写表名，Linux / Windows MySQL 通用
--   3. 可重复执行（DROP TABLE IF EXISTS 重建，注意：会清空已有数据！）
--
-- 执行方式：
--   mysql -uroot -p < init-all.sql
--   或在宝塔 phpMyAdmin / 数据库管理界面中导入本文件
--
-- 默认超管账号：admin / Admin@2026（must_change_password=0，首次登录不强制改密，
--               验收通过后请立即在「账号管理」中修改密码）
-- ============================================================

CREATE DATABASE IF NOT EXISTS `product_trace`
    DEFAULT CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;
USE `product_trace`;

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ============================================================
-- 1. 后台管理员表（含 v3 密码策略字段 + v6 RBAC role_id）
--    role_id = NULL 表示超级管理员（拥有全部权限，不经过 sys_role 校验）
-- ============================================================
DROP TABLE IF EXISTS `sys_admin`;
CREATE TABLE `sys_admin` (
    `id`                   BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `username`             VARCHAR(64)  NOT NULL COMMENT '登录账号',
    `password`             VARCHAR(100) NOT NULL COMMENT 'BCrypt 加密密码',
    `nickname`             VARCHAR(64)  DEFAULT NULL COMMENT '昵称',
    `avatar`               VARCHAR(512) DEFAULT NULL COMMENT '头像 URL',
    `role`                 VARCHAR(32)  NOT NULL DEFAULT 'ADMIN' COMMENT '角色（旧字段，兼容保留）：ADMIN/OPERATOR',
    `role_id`              BIGINT       DEFAULT NULL COMMENT '角色ID，NULL 为超级管理员',
    `status`               TINYINT      NOT NULL DEFAULT 1 COMMENT '状态：1-启用 0-禁用',
    `must_change_password` TINYINT      NOT NULL DEFAULT 0 COMMENT '1-必须先修改初始密码（改密前除 /admin/auth/** 外的接口一律 403）',
    `password_update_time` DATETIME     DEFAULT NULL COMMENT '最后一次修改密码时间（用于 90 天强制更换策略）',
    `last_login_time`      DATETIME     DEFAULT NULL COMMENT '最后登录时间',
    `last_login_ip`        VARCHAR(64)  DEFAULT NULL COMMENT '最后登录 IP',
    `deleted`              TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除：0-正常 1-删除',
    `create_time`          DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`          DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_username` (`username`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='后台管理员';

-- 默认超级管理员：admin / Admin@2026（BCrypt）
-- must_change_password = 0：首次登录可直接进入后台，验收后请立即改密
INSERT INTO `sys_admin`
    (`username`, `password`, `nickname`, `role`, `role_id`, `status`, `must_change_password`, `password_update_time`)
VALUES
    ('admin', '$2a$10$08fFat/KOmt3NsnNAkScs./W2i.Ezwl8U6Qb7.Uqt1epCn7Bn8B7q',
     '超级管理员', 'ADMIN', NULL, 1, 0, NOW());

-- ============================================================
-- 2. 后台角色表（v6 RBAC）
--    permissions 为 JSON 数组字符串，模块 key 全集：
--    ["product","code","stats","config","audit","media","system"]
-- ============================================================
DROP TABLE IF EXISTS `sys_role`;
CREATE TABLE `sys_role` (
    `id`          BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键',
    `role_code`   VARCHAR(64) NOT NULL COMMENT '角色编码（唯一）',
    `role_name`   VARCHAR(64) NOT NULL COMMENT '角色名称',
    `description` VARCHAR(255) DEFAULT NULL COMMENT '描述',
    `permissions` TEXT         DEFAULT NULL COMMENT 'JSON数组，模块key',
    `status`      TINYINT     NOT NULL DEFAULT 1 COMMENT '状态：1-启用 0-禁用',
    `create_time` DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_role_code` (`role_code`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='后台角色';

-- ============================================================
-- 3. 产品表
-- ============================================================
DROP TABLE IF EXISTS `product`;
CREATE TABLE `product` (
    `id`             BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
    `product_no`     VARCHAR(64)   NOT NULL COMMENT '产品业务编号（货号，后台录入）',
    `product_name`   VARCHAR(128)  NOT NULL COMMENT '产品名称',
    `category`       VARCHAR(64)   DEFAULT NULL COMMENT '产品分类（来源 sys_dict category）',
    `brand`          VARCHAR(64)   DEFAULT NULL COMMENT '品牌（来源 sys_dict brand）',
    `cover_image`    VARCHAR(512)  DEFAULT NULL COMMENT '封面图 URL',
    `image_list`     TEXT          DEFAULT NULL COMMENT '轮播图 URL 列表（JSON 数组）',
    `video_url`      VARCHAR(512)  DEFAULT NULL COMMENT '产品视频 URL（本地文件或视频直链）',
    `description`    LONGTEXT      DEFAULT NULL COMMENT '产品详情（富文本）',
    `origin`         VARCHAR(256)  DEFAULT NULL COMMENT '产地',
    `spec`           VARCHAR(128)  DEFAULT NULL COMMENT '规格',
    `thickness`      VARCHAR(50)   DEFAULT NULL COMMENT '厚度',
    `color`          VARCHAR(50)   DEFAULT NULL COMMENT '颜色',
    `size`           VARCHAR(50)   DEFAULT NULL COMMENT '尺码',
    `feature`        VARCHAR(255)  DEFAULT NULL COMMENT '特点',
    `crotch_type`    VARCHAR(50)   DEFAULT NULL COMMENT '裆型',
    `material`       VARCHAR(100)  DEFAULT NULL COMMENT '材质',
    `composition`    VARCHAR(255)  DEFAULT NULL COMMENT '成分',
    `display_fields` VARCHAR(1024) DEFAULT NULL COMMENT '用户端展示字段（JSON 数组，未配置展示全部非空字段）',
    `shelf_status`   TINYINT       NOT NULL DEFAULT 1 COMMENT '上架状态：1-上架 0-下架',
    `view_count`     BIGINT        NOT NULL DEFAULT 0 COMMENT '累计扫码/浏览次数',
    `retail_price`   DECIMAL(10,2) DEFAULT NULL COMMENT '统一零售价',
    `sort_order`     INT           NOT NULL DEFAULT 100 COMMENT '排序值（越大越靠前）',
    `deleted`        TINYINT       NOT NULL DEFAULT 0 COMMENT '逻辑删除：0-正常 1-删除',
    `create_time`    DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`    DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_product_no` (`product_no`),
    KEY `idx_category` (`category`),
    KEY `idx_shelf_status` (`shelf_status`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='产品信息';

-- ============================================================
-- 4. 基础数据字典（品牌 / 分类，后台维护，产品表单下拉选择）
-- ============================================================
DROP TABLE IF EXISTS `sys_dict`;
CREATE TABLE `sys_dict` (
    `id`          BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键',
    `dict_type`   VARCHAR(32) NOT NULL COMMENT '字典类型：brand-品牌 category-分类',
    `name`        VARCHAR(64) NOT NULL COMMENT '名称',
    `sort_order`  INT         NOT NULL DEFAULT 0 COMMENT '排序值（越大越靠前）',
    `create_time` DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    KEY `idx_type` (`dict_type`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='基础数据字典';

-- ============================================================
-- 5. 站点 / 首页配置（控制用户端 H5/小程序首页展示）
-- ============================================================
DROP TABLE IF EXISTS `sys_site_config`;
CREATE TABLE `sys_site_config` (
    `id`           BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键',
    `config_key`   VARCHAR(64) NOT NULL COMMENT '配置项键',
    `config_value` TEXT        DEFAULT NULL COMMENT '值（纯文本或 JSON）',
    `update_time`  DATETIME    DEFAULT NULL COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_key` (`config_key`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='站点/首页配置';

-- ============================================================
-- 6. 二维码表（普通产品码 + 一物一码，含 v2 防伪计数字段）
--    code_type = 1：普通产品码，同产品共用，scan_limit 一般为 0（不限）
--    code_type = 2：一物一码，每个实例唯一，scan_limit 默认 3~5
--    二维码图片由 Hutool/ZXing 本地生成，内容为 H5 短链，不依赖微信接口
-- ============================================================
DROP TABLE IF EXISTS `product_code`;
CREATE TABLE `product_code` (
    `id`                    BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键',
    `product_id`            BIGINT      NOT NULL COMMENT '关联产品 ID',
    `code`                  VARCHAR(32) NOT NULL COMMENT '码值（UUID 短码，大写字母+数字）',
    `code_type`             TINYINT     NOT NULL COMMENT '码类型：1-普通产品码 2-一物一码',
    `batch_no`              VARCHAR(64) DEFAULT NULL COMMENT '批次号（一物一码批量生成时写入）',
    `qr_image_url`          VARCHAR(512) DEFAULT NULL COMMENT '二维码图片 URL（H5 短链内容）',
    `scan_limit`            INT         NOT NULL DEFAULT 0 COMMENT '扫码次数上限：0-不限；一物一码默认 3',
    `scan_count`            INT         NOT NULL DEFAULT 0 COMMENT '累计正常扫码次数',
    `void_scan_count`       INT         NOT NULL DEFAULT 0 COMMENT '作废状态下被扫码次数',
    `over_limit_scan_count` INT         NOT NULL DEFAULT 0 COMMENT '超限后继续被扫码次数',
    `first_scan_time`       DATETIME    DEFAULT NULL COMMENT '首次扫码时间',
    `last_scan_time`        DATETIME    DEFAULT NULL COMMENT '最近扫码时间',
    `status`                TINYINT     NOT NULL DEFAULT 1 COMMENT '状态：1-有效 0-作废',
    `deleted`               TINYINT     NOT NULL DEFAULT 0 COMMENT '逻辑删除：0-正常 1-删除',
    `create_time`           DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`           DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_code` (`code`),
    KEY `idx_product_id` (`product_id`),
    KEY `idx_product_id_id` (`product_id`, `id`),
    KEY `idx_code_type` (`code_type`),
    KEY `idx_batch_no` (`batch_no`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='二维码（普通码/一物一码）';

-- ============================================================
-- 7. 扫码日志表（含 v3 统计索引 + v5 IP 地理位置字段）
--    scan_result：1-正常 2-超限/假货预警 3-无效码 4-作废码扫码 5-超限后继续扫
-- ============================================================
DROP TABLE IF EXISTS `scan_record`;
CREATE TABLE `scan_record` (
    `id`            BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
    `code_id`       BIGINT        DEFAULT NULL COMMENT '二维码 ID（无效码时为 NULL）',
    `code`          VARCHAR(32)   NOT NULL COMMENT '扫到的码值',
    `product_id`    BIGINT        DEFAULT NULL COMMENT '产品 ID',
    `scan_ip`       VARCHAR(64)   DEFAULT NULL COMMENT '扫码 IP',
    `scan_country`  VARCHAR(32)   DEFAULT NULL COMMENT '国家，如 中国',
    `scan_province` VARCHAR(32)   DEFAULT NULL COMMENT '省份，如 广东省',
    `scan_city`     VARCHAR(32)   DEFAULT NULL COMMENT '城市，如 深圳市',
    `scan_lat`      DECIMAL(10,6) DEFAULT NULL COMMENT '纬度（IP 库城市中心点近似值）',
    `scan_lng`      DECIMAL(10,6) DEFAULT NULL COMMENT '经度（IP 库城市中心点近似值）',
    `scan_source`   TINYINT       NOT NULL DEFAULT 0 COMMENT '扫码来源：0-未知 1-微信小程序 2-H5',
    `user_agent`    VARCHAR(512)  DEFAULT NULL COMMENT 'User-Agent（截断）',
    `scan_result`   TINYINT       NOT NULL DEFAULT 1 COMMENT '扫码结果：1-正常 2-假货预警 3-无效码 4-作废码 5-超限后继续扫',
    `is_fake`       TINYINT       NOT NULL DEFAULT 0 COMMENT '是否触发假货预警：0-否 1-是',
    `scan_time`     DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '扫码时间',
    PRIMARY KEY (`id`),
    KEY `idx_code_id` (`code_id`),
    KEY `idx_code_scan_time` (`code`, `scan_time`),
    KEY `idx_scan_time` (`scan_time`),
    KEY `idx_is_fake` (`is_fake`),
    KEY `idx_product_id` (`product_id`),
    KEY `idx_scan_result` (`scan_result`),
    KEY `idx_scan_ip` (`scan_ip`),
    KEY `idx_scan_province` (`scan_province`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='扫码日志';

-- ============================================================
-- 8. 媒体文件表（图片/视频统一登记）
-- ============================================================
DROP TABLE IF EXISTS `media_file`;
CREATE TABLE `media_file` (
    `id`           BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `file_name`    VARCHAR(256) NOT NULL COMMENT '原始文件名',
    `storage_key`  VARCHAR(512) NOT NULL COMMENT '存储 key（相对路径/对象名）',
    `file_url`     VARCHAR(512) NOT NULL COMMENT '对外访问 URL',
    `media_type`   VARCHAR(16)  NOT NULL COMMENT '媒体类型：image/video/other',
    `file_size`    BIGINT       NOT NULL DEFAULT 0 COMMENT '文件大小（字节）',
    `mime_type`    VARCHAR(128) DEFAULT NULL COMMENT 'MIME 类型',
    `storage_type` VARCHAR(16)  NOT NULL COMMENT '存储类型：local/oss/vod',
    `width`        INT          DEFAULT NULL COMMENT '图片宽度',
    `height`       INT          DEFAULT NULL COMMENT '图片高度',
    `duration`     INT          DEFAULT NULL COMMENT '视频时长（秒）',
    `deleted`      TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除：0-正常 1-删除',
    `create_time`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    KEY `idx_storage_key` (`storage_key`),
    KEY `idx_media_type` (`media_type`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='媒体文件';

-- ============================================================
-- 9. 一物一码批量生成任务表（v2，DB 持久化）
--    注意：generated 是 MySQL 8.0 保留字，Java 侧已用反引号映射
-- ============================================================
DROP TABLE IF EXISTS `batch_code_task`;
CREATE TABLE `batch_code_task` (
    `id`            BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `task_id`       VARCHAR(64)  NOT NULL COMMENT '异步任务 ID（Redis key 用）',
    `product_id`    BIGINT       NOT NULL COMMENT '产品 ID',
    `product_name`  VARCHAR(200) DEFAULT NULL COMMENT '产品名称（冗余快照）',
    `batch_no`      VARCHAR(64)  NOT NULL COMMENT '批次号',
    `total`         INT          NOT NULL COMMENT '计划生成总数',
    `generated`     INT          NOT NULL DEFAULT 0 COMMENT '实际生成数',
    `scan_limit`    INT          NOT NULL DEFAULT 3 COMMENT '本次批次扫码次数上限',
    `status`        VARCHAR(16)  NOT NULL DEFAULT 'RUNNING' COMMENT 'RUNNING/SUCCESS/FAILED',
    `csv_file_path` VARCHAR(500) DEFAULT NULL COMMENT 'CSV 物理文件路径',
    `download_url`  VARCHAR(200) DEFAULT NULL COMMENT 'CSV 下载接口路径',
    `message`       VARCHAR(500) DEFAULT NULL COMMENT '失败原因/备注',
    `start_time`    DATETIME     DEFAULT NULL COMMENT '任务开始时间',
    `end_time`      DATETIME     DEFAULT NULL COMMENT '任务结束时间',
    `create_time`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_task_id` (`task_id`),
    KEY `idx_product_id` (`product_id`),
    KEY `idx_batch_no` (`batch_no`),
    KEY `idx_status` (`status`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='一物一码批量生成任务';

-- ============================================================
-- 10. 后台操作审计日志表（v4）
-- ============================================================
DROP TABLE IF EXISTS `admin_operation_log`;
CREATE TABLE `admin_operation_log` (
    `id`             BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `admin_id`       BIGINT       DEFAULT NULL COMMENT '操作人ID（未登录/系统触发时为空）',
    `admin_username` VARCHAR(64)  DEFAULT NULL COMMENT '操作人用户名（冗余快照，账号删除后仍可追溯）',
    `module`         VARCHAR(50)  NOT NULL COMMENT '业务模块，如 产品/二维码/站点配置/账号',
    `action`         VARCHAR(50)  NOT NULL COMMENT '动作，如 新增/编辑/删除/作废/批量生成',
    `method`         VARCHAR(150) DEFAULT NULL COMMENT '完整方法签名（类名.方法名）',
    `request_uri`    VARCHAR(255) DEFAULT NULL COMMENT '请求路径',
    `http_method`    VARCHAR(10)  DEFAULT NULL COMMENT 'GET/POST/PUT/DELETE',
    `target_id`      VARCHAR(64)  DEFAULT NULL COMMENT '操作对象ID（从路径变量或参数中提取）',
    `request_params` TEXT         DEFAULT NULL COMMENT '请求参数（已脱敏，最多 2000 字符）',
    `response_code`  INT          DEFAULT NULL COMMENT '业务返回码，200=成功',
    `error_msg`      VARCHAR(500) DEFAULT NULL COMMENT '失败时的异常摘要',
    `ip`             VARCHAR(64)  DEFAULT NULL COMMENT '客户端 IP',
    `cost_ms`        INT          DEFAULT NULL COMMENT '耗时（毫秒）',
    `created_at`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '操作时间',
    PRIMARY KEY (`id`),
    KEY `idx_created_at` (`created_at`),
    KEY `idx_admin_id` (`admin_id`),
    KEY `idx_module_action` (`module`, `action`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='后台操作审计日志';

-- ============================================================
-- 11. 媒体存储配置表（后台「存储配置」页面，单行配置 id=1）
--     保存后即时生效，上传图片/视频、生成二维码都按这里配置落盘
-- ============================================================
DROP TABLE IF EXISTS `sys_storage_config`;
CREATE TABLE `sys_storage_config` (
    `id`                    BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键（固定 1）',
    `storage_type`          VARCHAR(16)  NOT NULL DEFAULT 'local' COMMENT '存储类型：local-本地 oss-阿里云OSS',
    `local_root_path`       VARCHAR(255) DEFAULT '/www/wwwroot/product-trace/upload' COMMENT '本地存储绝对路径',
    `local_url_prefix`      VARCHAR(255) DEFAULT '/upload' COMMENT '本地对外 URL 前缀，留空返回相对路径',
    `oss_access_key_id`     VARCHAR(128) DEFAULT NULL COMMENT 'OSS AccessKey ID',
    `oss_access_key_secret` VARCHAR(255) DEFAULT NULL COMMENT 'OSS AccessKey Secret',
    `oss_bucket_name`       VARCHAR(128) DEFAULT NULL COMMENT 'OSS Bucket 名称',
    `oss_endpoint`          VARCHAR(128) DEFAULT NULL COMMENT 'OSS Endpoint，如 oss-cn-hangzhou.aliyuncs.com',
    `oss_url_prefix`        VARCHAR(255) DEFAULT NULL COMMENT 'OSS 自定义访问域名/CDN，留空用 bucket.endpoint',
    `update_time`           DATETIME     DEFAULT NULL COMMENT '更新时间',
    PRIMARY KEY (`id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='媒体存储配置';

INSERT INTO `sys_storage_config`
    (`id`, `storage_type`, `local_root_path`, `local_url_prefix`, `update_time`)
VALUES
    (1, 'local', '/www/wwwroot/product-trace/upload', '/upload', NOW());

SET FOREIGN_KEY_CHECKS = 1;

-- ============================================================
-- 结构自检输出（导入后人工核对：应为 11 张表）
-- ============================================================
SELECT '=== 初始化完成，表清单 ===' AS section;
SHOW TABLES;

SELECT '=== 默认超管账号 admin / Admin@2026（首次登录后请立即修改密码）===' AS section;
SELECT id, username, nickname, role, role_id, status, must_change_password FROM sys_admin;
