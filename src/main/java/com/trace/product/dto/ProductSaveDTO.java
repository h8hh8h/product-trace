package com.trace.product.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 产品新增/编辑参数
 */
@Data
public class ProductSaveDTO {

    /** 编辑时必填 */
    private Long id;

    @NotBlank(message = "产品编号不能为空")
    private String productNo;

    @NotBlank(message = "产品名称不能为空")
    private String productName;

    private String category;

    private String brand;

    private String coverImage;

    /** 轮播图 URL JSON 数组字符串 */
    private String imageList;

    private String videoUrl;

    /** 图文详情富文本 HTML */
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

    /** 用户端展示字段（JSON 数组字符串） */
    private String displayFields;

    /** 统一零售价 */
    private java.math.BigDecimal retailPrice;

    @NotNull(message = "上架状态不能为空")
    private Integer shelfStatus;

    private Integer sortOrder;
}
