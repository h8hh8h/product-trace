package com.trace.product.dto;

import lombok.Builder;
import lombok.Data;

/**
 * 普通产品码生成结果
 */
@Data
@Builder
public class NormalCodeVO {

    private Long productId;

    private String productName;

    private String code;

    /** 小程序码图片 URL */
    private String qrImageUrl;

    private Integer scanLimit;

    private Boolean mock;
}
