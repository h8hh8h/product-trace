package com.trace.product.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 后台接口模块权限标记。
 * 打在 Controller 类或方法上，由 PermissionInterceptor 校验当前管理员的角色权限。
 * 超级管理员（sys_admin.role_id 为 NULL）直接放行。
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface RequirePerm {

    /** 模块 key，如 product / code / stats / config / audit / media / system */
    String value();
}
