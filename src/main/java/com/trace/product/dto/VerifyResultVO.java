package com.trace.product.dto;

import lombok.Builder;
import lombok.Data;

/**
 * 防伪验证结果
 */
@Data
@Builder
public class VerifyResultVO {

    /** 是否正品 */
    private Boolean valid;

    /** 是否触发假货/超限预警（scanResult=2 或 5） */
    private Boolean fakeWarning;

    /** 是否作废码扫码（scanResult=4） */
    private Boolean voidWarning;

    /** 是否超限后继续扫（scanResult=5，比首次超限更严重的警报） */
    private Boolean overLimitContinue;

    private String code;

    private Long productId;

    private String productName;

    private String coverImage;

    /** 当前累计扫码次数 */
    private Integer scanCount;

    /** 扫码次数上限（0 表示不限） */
    private Integer scanLimit;

    private String message;
}
