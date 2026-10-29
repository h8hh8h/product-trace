package com.trace.product.dto;

import lombok.Data;

/**
 * 扫码热门产品
 */
@Data
public class TopProductVO {

    private Long id;

    private String productNo;

    private String productName;

    /** 扫码次数 */
    private Long scanCount;
}
