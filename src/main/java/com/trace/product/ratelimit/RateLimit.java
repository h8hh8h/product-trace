package com.trace.product.ratelimit;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 接口限流注解（基于 IP + Redis Lua 脚本）
 * <p>
 * 用法：在 Controller 方法上标注 @RateLimit(limit = 10, time = 60)
 * 表示该接口同一 IP 在 60 秒内最多访问 10 次，超限返回 429。
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RateLimit {

    /** 时间窗口内最大请求数 */
    int limit() default 10;

    /** 时间窗口（秒） */
    int time() default 60;

    /** 自定义 key 前缀，为空时使用方法名 */
    String key() default "";
}
