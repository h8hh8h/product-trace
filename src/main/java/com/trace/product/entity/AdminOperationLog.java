package com.trace.product.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 后台操作审计日志
 */
@Data
@TableName("admin_operation_log")
public class AdminOperationLog {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 操作人 ID */
    private Long adminId;

    /** 操作人用户名（冗余快照） */
    private String adminUsername;

    /** 业务模块，如 产品 / 二维码 / 站点配置 / 账号 */
    private String module;

    /** 动作，如 新增 / 编辑 / 删除 / 作废 / 批量生成 */
    private String action;

    /** 完整方法签名 */
    private String method;

    private String requestUri;

    private String httpMethod;

    /** 操作对象 ID */
    private String targetId;

    /** 请求参数（已脱敏） */
    private String requestParams;

    /** 业务返回码，200 表示成功 */
    private Integer responseCode;

    /** 失败时的异常摘要 */
    private String errorMsg;

    private String ip;

    /** 耗时（毫秒） */
    private Integer costMs;

    private LocalDateTime createdAt;
}
