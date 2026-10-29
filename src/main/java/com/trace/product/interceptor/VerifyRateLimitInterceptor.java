package com.trace.product.interceptor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.trace.product.common.Result;
import com.trace.product.common.ResultCode;
import com.trace.product.util.IpUtils;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;

/**
 * 防伪验证接口 IP 限流拦截器
 * <p>
 * 基于 Redis + Lua 脚本实现固定窗口限流：
 * 同一 IP 每 window 秒最多 limit 次，超出直接返回 HTTP 429。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class VerifyRateLimitInterceptor implements HandlerInterceptor {

    private static final String KEY_PREFIX = "rate:verify:ip:";

    private final StringRedisTemplate stringRedisTemplate;
    private final DefaultRedisScript<List> verifyRateLimitScript;
    private final ObjectMapper objectMapper;

    @Value("${trace.rate-limit.verify.limit:30}")
    private int limit;

    @Value("${trace.rate-limit.verify.window:60}")
    private int window;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        // 预检请求直接放行
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }

        String ip = IpUtils.getClientIp(request);
        String key = KEY_PREFIX + ip;

        List<?> result = stringRedisTemplate.execute(
                verifyRateLimitScript,
                Collections.singletonList(key),
                String.valueOf(limit),
                String.valueOf(window)
        );

        long allowed = 0;
        long ttl = window;
        long current = 0;
        if (result != null && result.size() >= 3) {
            allowed = ((Number) result.get(0)).longValue();
            ttl = ((Number) result.get(1)).longValue();
            current = ((Number) result.get(2)).longValue();
        }

        response.setHeader("X-RateLimit-Limit", String.valueOf(limit));
        response.setHeader("X-RateLimit-Remaining", String.valueOf(Math.max(0, limit - current)));
        response.setHeader("X-RateLimit-Window", String.valueOf(window));

        if (allowed == 0) {
            log.warn("防伪接口触发限流: ip={}, limit={}/{}s, retryAfter={}s", ip, limit, window, ttl);
            response.setStatus(ResultCode.RATE_LIMITED.getCode());
            response.setHeader("Retry-After", String.valueOf(Math.max(ttl, 1)));
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.getWriter().write(objectMapper.writeValueAsString(
                    Result.fail(ResultCode.RATE_LIMITED.getCode(),
                            "请求过于频繁，同一 IP 每分钟最多 " + limit + " 次，请稍后再试")
            ));
            return false;
        }

        return true;
    }
}
