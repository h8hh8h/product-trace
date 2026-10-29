package com.trace.product.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 产品
 */
@Data
@TableName("product")
public class Product {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 产品业务编号 */
    private String productNo;

    /** 产品名称 */
    private String productName;

    private String category;

    private String brand;

    /** 封面图 URL */
    private String coverImage;

    /** 轮播图 URL 列表（JSON 数组字符串） */
    private String imageList;

    /** 产品视频 URL */
    private String videoUrl;

    /** 图文详情（富文本 HTML） */
    private String description;

    private String origin;

    private String spec;

    /** 厚度 */
    private String thickness;

    /** 颜色 */
    private String color;

    /** 尺码 */
    private String size;

    /** 特点 */
    private String feature;

    /** 裆型 */
    private String crotchType;

    /** 材质 */
    private String material;

    /** 成分 */
    private String composition;

    /** 用户端展示字段（JSON 数组字符串，如 ["productNo","brand"]） */
    private String displayFields;

    /** 统一零售价 */
    private java.math.BigDecimal retailPrice;

    /** 1-上架 0-下架 */
    private Integer shelfStatus;

    private Long viewCount;

    private Integer sortOrder;

    @TableLogic
    private Integer deleted;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
