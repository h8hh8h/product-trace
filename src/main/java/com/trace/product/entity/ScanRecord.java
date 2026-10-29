package com.trace.product.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 扫码日志
 */
@Data
@TableName("scan_record")
public class ScanRecord {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long codeId;

    private String code;

    private Long productId;

    private String scanIp;

    /** 国家（离线 IP 库解析，可能为空） */
    private String scanCountry;

    /** 省份，如 广东省 */
    private String scanProvince;

    /** 城市，如 深圳市 */
    private String scanCity;

    /** 纬度（IP 库城市中心点近似值） */
    private BigDecimal scanLat;

    /** 经度（IP 库城市中心点近似值） */
    private BigDecimal scanLng;

    /** 0-未知 1-微信小程序 2-H5 */
    private Integer scanSource;

    private String userAgent;

    /**
     * 扫码结果枚举：
     *   1 = 正常扫码（码有效 + 未超限）
     *   2 = 超限/假货预警（第一次触发超限阈值）
     *   3 = 无效码（码不存在 / 被逻辑删 / 产品下架）
     *   4 = 作废码扫码（码在 product_code.status=0 时被扫）
     *   5 = 超限后继续扫（scanLimit>0 且 scanCount>scanLimit 时再次扫）
     */
    private Integer scanResult;

    private Integer isFake;

    private LocalDateTime scanTime;
}
