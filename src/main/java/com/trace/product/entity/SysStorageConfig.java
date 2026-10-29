package com.trace.product.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 媒体存储配置（单行配置表，id 固定为 1）
 * <p>
 * 后台「存储配置」页面读写这张表，上传图片/视频、生成二维码时动态读取。
 */
@Data
@TableName("sys_storage_config")
public class SysStorageConfig {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 存储类型：local-本地服务器 oss-阿里云OSS */
    private String storageType;

    /** 本地存储根目录（绝对路径） */
    private String localRootPath;

    /** 本地存储对外 URL 前缀，留空则返回相对路径 /upload */
    private String localUrlPrefix;

    private String ossAccessKeyId;

    private String ossAccessKeySecret;

    private String ossBucketName;

    /** 地域节点，如 oss-cn-hangzhou.aliyuncs.com */
    private String ossEndpoint;

    /** 自定义访问域名/CDN，留空则用 https://{bucket}.{endpoint} */
    private String ossUrlPrefix;

    private LocalDateTime updateTime;
}
