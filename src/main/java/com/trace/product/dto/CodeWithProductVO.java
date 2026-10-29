package com.trace.product.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 码列表分页 VO（JOIN 产品表带出基础信息）
 */
@Data
public class CodeWithProductVO {

    private Long id;
    private Long productId;
    private String code;
    private Integer codeType;
    private String batchNo;
    private String qrImageUrl;
    private Integer scanLimit;
    private Integer scanCount;

    /** 作废状态下被扫次数（row 级聚合自 scan_record.scan_result=4） */
    private Integer voidScanCount;

    /** 超限后继续被扫次数（row 级聚合自 scan_record.scan_result=2 且码已超限） */
    private Integer overLimitScanCount;

    private LocalDateTime firstScanTime;
    private LocalDateTime lastScanTime;
    private Integer status;
    private LocalDateTime createTime;

    // 产品信息
    private String productNo;
    private String productName;
    private String category;
    private String brand;
    private String spec;
}
