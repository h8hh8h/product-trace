package com.trace.product.dto;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 一物一码批量生成进度（存储于 Redis）
 */
@Data
public class BatchTaskProgress implements Serializable {

    public static final String STATUS_RUNNING = "RUNNING";
    public static final String STATUS_SUCCESS = "SUCCESS";
    public static final String STATUS_FAILED = "FAILED";

    private String taskId;

    private Long productId;

    private String productName;

    private String batchNo;

    /** 计划生成总数 */
    private Integer total;

    /** 已生成数量 */
    private Integer generated = 0;

    /** 本次批次扫码次数上限（0=不限） */
    private Integer scanLimit;

    /** RUNNING / SUCCESS / FAILED */
    private String status = STATUS_RUNNING;

    private String message;

    /** 样例码值（最多 5 个，便于测试） */
    private List<String> sampleCodes = new ArrayList<>();

    /** CSV 下载地址（相对接口路径） */
    private String downloadUrl;

    private LocalDateTime startTime;

    private LocalDateTime endTime;
}
