package com.trace.product.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 二维码（普通产品码 / 一物一码）
 */
@Data
@TableName("product_code")
public class ProductCode {

    /** 码类型：普通产品码 */
    public static final int TYPE_NORMAL = 1;
    /** 码类型：一物一码 */
    public static final int TYPE_UNIQUE = 2;

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long productId;

    /** 码值（UUID 短码） */
    private String code;

    /** 1-普通产品码 2-一物一码 */
    private Integer codeType;

    /** 批次号 */
    private String batchNo;

    /** 小程序码图片 URL */
    private String qrImageUrl;

    /** 扫码次数上限：0-不限 */
    private Integer scanLimit;

    /** 累计正常扫码次数 */
    private Integer scanCount;

    /** 作废状态下被扫码次数（status=0 时被扫） */
    private Integer voidScanCount;

    /** 超限后继续被扫码次数（scanLimit>0 且 scanCount>scanLimit 时再次扫） */
    private Integer overLimitScanCount;

    private LocalDateTime firstScanTime;

    private LocalDateTime lastScanTime;

    /** 1-有效 0-作废 */
    private Integer status;

    @TableLogic
    private Integer deleted;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
