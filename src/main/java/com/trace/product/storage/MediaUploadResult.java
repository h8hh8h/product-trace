package com.trace.product.storage;

import lombok.Builder;
import lombok.Data;

/**
 * 媒体上传结果
 */
@Data
@Builder
public class MediaUploadResult {

    /** 存储 key（相对路径/对象名），用于删除 */
    private String storageKey;

    /** 对外可访问 URL */
    private String fileUrl;

    /** 原始文件名 */
    private String fileName;

    /** 文件大小（字节） */
    private Long fileSize;

    /** MIME 类型 */
    private String mimeType;

    /** 媒体类型 image/video/other */
    private String mediaType;

    /** 存储类型 */
    private String storageType;

    /** 图片宽度 */
    private Integer width;

    /** 图片高度 */
    private Integer height;

    /** 视频时长（秒） */
    private Integer duration;
}
