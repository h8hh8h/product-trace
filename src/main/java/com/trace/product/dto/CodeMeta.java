package com.trace.product.dto;

import lombok.Data;

import java.io.Serializable;

/**
 * 码信息缓存（Redis）
 */
@Data
public class CodeMeta implements Serializable {

    private Long codeId;

    private String code;

    private Long productId;

    private Integer scanLimit;

    private Integer scanCount;

    /** 1-有效 0-作废 */
    private Integer status;

    private String productName;

    private String coverImage;

    /** 1-上架 0-下架 */
    private Integer shelfStatus;
}
