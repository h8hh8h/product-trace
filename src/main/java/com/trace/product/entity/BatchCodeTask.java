package com.trace.product.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 一物一码批量生成任务（DB 持久化，替代纯 Redis 存储）
 */
@Data
@TableName("batch_code_task")
public class BatchCodeTask {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 异步任务 ID（Redis key 用） */
    private String taskId;

    private Long productId;

    private String productName;

    private String batchNo;

    private Integer total;

    /** 已生成数量（MySQL 8.0 reserved word，加反引号） */
    @TableField("`generated`")
    private Integer generated;

    /** 本次批次扫码次数上限 */
    private Integer scanLimit;

    /** RUNNING / SUCCESS / FAILED */
    private String status;

    private String csvFilePath;

    private String downloadUrl;

    private String message;

    private LocalDateTime startTime;

    private LocalDateTime endTime;

    private LocalDateTime createTime;
}
