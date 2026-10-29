package com.trace.product.ratelimit;

import com.trace.product.common.RateLimitException;
import com.trace.product.util.IpUtils;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.lang.reflect.Method;
import java.util.Collections;
import java.util.List;

/**
 * 限流切面：结合 Redis Lua 脚本实现基于 IP 的原子限流
 */
@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class RateLimitAspect {

    private final RedisTemplate<String, Object> redisTemplate;
    private final DefaultRedisScript<Long> rateLimitScript;

    private static final String KEY_PREFIX = "rate_limit:";

    @Around("@annotation(rateLimit)")
    public Object around(ProceedingJoinPoint joinPoint, RateLimit rateLimit) throws Throwable {
        HttpServletRequest request = getRequest();
        String ip = IpUtils.getClientIp(request);

        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        String keySuffix = rateLimit.key().isEmpty()
                ? method.getDeclaringClass().getSimpleName() + ":" + method.getName()
                : rateLimit.key();

        String key = KEY_PREFIX + ip + ":" + keySuffix;
        List<String> keys = Collections.singletonList(key);

        Long result = redisTemplate.execute(
                rateLimitScript,
                keys,
                String.valueOf(rateLimit.limit()),
                String.valueOf(rateLimit.time())
        );

        if (result == null || result == 0) {
            log.warn("IP[{}] 触发限流, key={}, limit={}/{}s", ip, keySuffix, rateLimit.limit(), rateLimit.time());
            throw new RateLimitException("请求过于频繁，请 " + rateLimit.time() + " 秒后再试");
        }

        return joinPoint.proceed();
    }

    private HttpServletRequest getRequest() {
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        return attributes != null ? attributes.getRequest() : null;
    }
}
