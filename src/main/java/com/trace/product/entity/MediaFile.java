package com.trace.product.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 媒体文件记录：所有上传成功的图片/视频落库，供后台「媒体库」检索复用
 */
@Data
@TableName("media_file")
public class MediaFile {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 原始文件名（含 {货号}_{时间戳} 命名） */
    private String fileName;

    /** 存储 key（相对路径/对象名） */
    private String storageKey;

    /** 对外访问 URL */
    private String fileUrl;

    /** 媒体类型：image/video/other */
    private String mediaType;

    /** 文件大小（字节） */
    private Long fileSize;

    private String mimeType;

    /** 存储类型：local/oss */
    private String storageType;

    private Integer width;

    private Integer height;

    /** 视频时长（秒） */
    private Integer duration;

    /** 逻辑删除：0-正常 1-删除 */
    private Integer deleted;

    private LocalDateTime createTime;
}
