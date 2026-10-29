-- ============================================================
-- 产品数字化展示与一物一码溯源系统 - 数据库初始化脚本
-- MySQL 8.0+ / InnoDB / utf8mb4
-- 执行方式：mysql -uroot -p < deploy/schema.sql
-- ============================================================

CREATE DATABASE IF NOT EXISTS `product_trace`
    DEFAULT CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;
USE `product_trace`;

-- ============================================================
-- 1. 后台管理员表
-- ============================================================
DROP TABLE IF EXISTS `sys_admin`;
CREATE TABLE `sys_admin` (
    `id`              BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `username`        VARCHAR(64)  NOT NULL COMMENT '登录账号',
    `password`        VARCHAR(100) NOT NULL COMMENT 'BCrypt 加密密码',
    `nickname`        VARCHAR(64)  DEFAULT NULL COMMENT '昵称',
    `avatar`          VARCHAR(512) DEFAULT NULL COMMENT '头像 URL',
    `role`            VARCHAR(32)  NOT NULL DEFAULT 'ADMIN' COMMENT '角色：ADMIN/OPERATOR',
    `status`          TINYINT      NOT NULL DEFAULT 1 COMMENT '状态：1-启用 0-禁用',
    `last_login_time` DATETIME     DEFAULT NULL COMMENT '最后登录时间',
    `last_login_ip`   VARCHAR(64)  DEFAULT NULL COMMENT '最后登录 IP',
    `deleted`         TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除：0-正常 1-删除',
    `create_time`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_username` (`username`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='后台管理员';

-- 默认管理员：admin / admin123
-- 初始账号：admin / admin123（BCrypt 加密存储，部署后请立即修改）
INSERT INTO `sys_admin` (`username`, `password`, `nickname`, `role`)
VALUES ('admin', '$2a$10$xNmD7s4EFfDxpnrkHuEkIe/S2prc74BsnCFpVxjZyQaHdacZEvoAG', '超级管理员', 'ADMIN');

-- ============================================================
-- 2. 产品表
-- ============================================================
DROP TABLE IF EXISTS `product`;
CREATE TABLE `product` (
    `id`             BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `product_no`     VARCHAR(64)  NOT NULL COMMENT '产品业务编号（后台录入）',
    `product_name`   VARCHAR(128) NOT NULL COMMENT '产品名称',
    `category`       VARCHAR(64)  DEFAULT NULL COMMENT '产品分类',
    `brand`          VARCHAR(64)  DEFAULT NULL COMMENT '品牌',
    `cover_image`    VARCHAR(512) DEFAULT NULL COMMENT '封面图 URL',
    `image_list`     TEXT         DEFAULT NULL COMMENT '轮播图 URL 列表（JSON 数组）',
    `video_url`      VARCHAR(512) DEFAULT NULL COMMENT '产品视频 URL',
    `description`    LONGTEXT     DEFAULT NULL COMMENT '产品详情（富文本）',
    `origin`         VARCHAR(256) DEFAULT NULL COMMENT '产地',
    `spec`           VARCHAR(128) DEFAULT NULL COMMENT '规格',
    `thickness`      VARCHAR(50)  DEFAULT NULL COMMENT '厚度',
    `color`          VARCHAR(50)  DEFAULT NULL COMMENT '颜色',
    `size`           VARCHAR(50)  DEFAULT NULL COMMENT '尺码',
    `feature`        VARCHAR(255) DEFAULT NULL COMMENT '特点',
    `crotch_type`    VARCHAR(50)  DEFAULT NULL COMMENT '裆型',
    `material`       VARCHAR(100) DEFAULT NULL COMMENT '材质',
    `composition`    VARCHAR(255) DEFAULT NULL COMMENT '成分',
    `display_fields` VARCHAR(1024) DEFAULT NULL COMMENT '用户端展示字段（JSON 数组）',
    `shelf_status`   TINYINT      NOT NULL DEFAULT 1 COMMENT '上架状态：1-上架 0-下架',
    `view_count`     BIGINT       NOT NULL DEFAULT 0 COMMENT '累计扫码/浏览次数',
    `retail_price`   DECIMAL(10,2) DEFAULT NULL COMMENT '统一零售价',
    `sort_order`     INT           NOT NULL DEFAULT 100 COMMENT '排序值（越大越靠前）',
    `deleted`        TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除：0-正常 1-删除',
    `create_time`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_product_no` (`product_no`),
    KEY `idx_category` (`category`),
    KEY `idx_shelf_status` (`shelf_status`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='产品信息';

-- ============================================================
-- 基础数据字典（品牌 / 分类，后台提前维护，产品表单下拉选择）
-- ============================================================
DROP TABLE IF EXISTS `sys_dict`;
CREATE TABLE `sys_dict` (
    `id`             BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键',
    `dict_type`      VARCHAR(32) NOT NULL COMMENT '字典类型：brand-品牌 category-分类',
    `name`           VARCHAR(64) NOT NULL COMMENT '名称',
    `sort_order`     INT         NOT NULL DEFAULT 0 COMMENT '排序值（越大越靠前）',
    `create_time`    DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    KEY `idx_type` (`dict_type`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='基础数据字典';

-- ============================================================
-- 站点 / 首页配置（可后台编辑，控制用户端小程序首页）
-- ============================================================
DROP TABLE IF EXISTS `sys_site_config`;
CREATE TABLE `sys_site_config` (
    `id`             BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `config_key`     VARCHAR(64)  NOT NULL COMMENT '配置项键',
    `config_value`   TEXT                  COMMENT '值（纯文本或 JSON）',
    `update_time`    DATETIME              COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_key` (`config_key`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='站点/首页配置';

-- ============================================================
-- 3. 二维码表（普通产品码 + 一物一码）
--    code_type = 1：普通产品码，同产品共用，scan_limit 一般为 0（不限）
--    code_type = 2：一物一码，每个实例唯一，scan_limit 默认 5
-- ============================================================
DROP TABLE IF EXISTS `product_code`;
CREATE TABLE `product_code` (
    `id`             BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键',
    `product_id`     BIGINT      NOT NULL COMMENT '关联产品 ID',
    `code`           VARCHAR(32) NOT NULL COMMENT '码值（UUID 短码，大写字母+数字）',
    `code_type`      TINYINT     NOT NULL COMMENT '码类型：1-普通产品码 2-一物一码',
    `batch_no`       VARCHAR(64) DEFAULT NULL COMMENT '批次号（一物一码批量生成时写入）',
    `qr_image_url`   VARCHAR(512) DEFAULT NULL COMMENT '小程序码图片 URL（普通产品码使用）',
    `scan_limit`     INT         NOT NULL DEFAULT 0 COMMENT '扫码次数上限：0-不限；一物一码默认 3',
    `scan_count`     INT         NOT NULL DEFAULT 0 COMMENT '累计扫码次数',
    `first_scan_time` DATETIME   DEFAULT NULL COMMENT '首次扫码时间',
    `last_scan_time`  DATETIME   DEFAULT NULL COMMENT '最近扫码时间',
    `status`         TINYINT     NOT NULL DEFAULT 1 COMMENT '状态：1-有效 0-作废',
    `deleted`        TINYINT     NOT NULL DEFAULT 0 COMMENT '逻辑删除：0-正常 1-删除',
    `create_time`    DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`    DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_code` (`code`),
    KEY `idx_product_id` (`product_id`),
    KEY `idx_code_type` (`code_type`),
    KEY `idx_batch_no` (`batch_no`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='二维码（普通码/一物一码）';

-- ============================================================
-- 4. 扫码日志表
--    scan_result：1-正常 2-假货预警（超次数） 3-无效码（不存在/作废）
-- ============================================================
DROP TABLE IF EXISTS `scan_record`;
CREATE TABLE `scan_record` (
    `id`           BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `code_id`      BIGINT       DEFAULT NULL COMMENT '二维码 ID（无效码时为 NULL）',
    `code`         VARCHAR(32)  NOT NULL COMMENT '扫到的码值',
    `product_id`   BIGINT       DEFAULT NULL COMMENT '产品 ID',
    `scan_ip`      VARCHAR(64)  DEFAULT NULL COMMENT '扫码 IP',
    `scan_source`  TINYINT      NOT NULL DEFAULT 0 COMMENT '扫码来源：0-未知 1-微信小程序 2-H5',
    `user_agent`   VARCHAR(512) DEFAULT NULL COMMENT 'User-Agent（截断）',
    `scan_result`  TINYINT      NOT NULL DEFAULT 1 COMMENT '扫码结果：1-正常 2-假货预警 3-无效码',
    `is_fake`      TINYINT      NOT NULL DEFAULT 0 COMMENT '是否触发假货预警：0-否 1-是',
    `scan_time`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '扫码时间',
    PRIMARY KEY (`id`),
    KEY `idx_code_id` (`code_id`),
    KEY `idx_code_scan_time` (`code`, `scan_time`),
    KEY `idx_scan_time` (`scan_time`),
    KEY `idx_is_fake` (`is_fake`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='扫码日志';

-- ============================================================
-- 5. 媒体文件表（图片/视频统一登记，配合策略模式）
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
