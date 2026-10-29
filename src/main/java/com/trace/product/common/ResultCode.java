package com.trace.product.common;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 响应码枚举
 */
@Getter
@AllArgsConstructor
public enum ResultCode {

    SUCCESS(200, "操作成功"),
    FAIL(500, "操作失败"),
    BAD_REQUEST(400, "请求参数错误"),
    UNAUTHORIZED(401, "未登录或登录已过期"),
    FORBIDDEN(403, "无访问权限"),
    NOT_FOUND(404, "资源不存在"),
    RATE_LIMITED(429, "请求过于频繁，请稍后再试"),

    // 业务码 1xxx
    CODE_NOT_FOUND(1001, "产品码不存在"),
    CODE_INVALID(1002, "产品码已失效"),
    PRODUCT_OFFLINE(1003, "产品已下架"),
    FAKE_WARNING(1004, "假货预警：该码扫码次数异常"),
    CODE_GENERATE_FAIL(1005, "二维码生成失败"),
    BATCH_TASK_NOT_FOUND(1006, "批量任务不存在或已过期"),
    BATCH_TASK_NOT_DONE(1007, "批量任务尚未完成，请稍后再试"),
    MEDIA_UPLOAD_FAIL(1101, "文件上传失败"),

    // 认证与安全 2xxx
    LOGIN_LOCKED(2001, "账号已被锁定，请稍后再试"),
    MUST_CHANGE_PASSWORD(2002, "请先修改初始密码"),
    PASSWORD_TOO_WEAK(2003, "密码强度不足"),
    ;

    private final Integer code;
    private final String message;
}
