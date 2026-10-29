package com.trace.product.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 后台操作审计标记。
 * 打在 Controller 方法上，由 AuditLogAspect 统一记录「谁 / 何时 / 做了什么 / 参数 / 结果 / 耗时」。
 *
 * <pre>
 * &#64;AuditLog(module = "产品", action = "删除")
 * </pre>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface AuditLog {

    /** 业务模块，如 产品 / 二维码 / 站点配置 / 账号 */
    String module();

    /** 动作，如 新增 / 编辑 / 删除 / 作废 / 批量生成 */
    String action();
}
