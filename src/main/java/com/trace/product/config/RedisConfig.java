package com.trace.product.config;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.jsontype.impl.LaissezFaireSubTypeValidator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.util.List;

/**
 * Redis 配置
 */
@Configuration
public class RedisConfig {

    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory factory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(factory);

        StringRedisSerializer stringSerializer = new StringRedisSerializer();

        Jackson2JsonRedisSerializer<Object> jacksonSerializer = new Jackson2JsonRedisSerializer<>(Object.class);
        ObjectMapper om = new ObjectMapper();
        om.setVisibility(PropertyAccessor.ALL, JsonAutoDetect.Visibility.ANY);
        om.activateDefaultTyping(LaissezFaireSubTypeValidator.instance, ObjectMapper.DefaultTyping.NON_FINAL);
        jacksonSerializer.setObjectMapper(om);

        template.setKeySerializer(stringSerializer);
        template.setHashKeySerializer(stringSerializer);
        template.setValueSerializer(jacksonSerializer);
        template.setHashValueSerializer(jacksonSerializer);
        template.afterPropertiesSet();
        return template;
    }

    /**
     * 限流 Lua 脚本（固定窗口 + TTL）
     * KEYS[1] = 限流 key
     * ARGV[1] = 最大次数
     * ARGV[2] = 过期时间（秒）
     * 返回：1=允许 0=拒绝
     */
    @Bean
    public DefaultRedisScript<Long> rateLimitScript() {
        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setScriptText(
                "local key = KEYS[1]\n" +
                "local limit = tonumber(ARGV[1])\n" +
                "local expire = tonumber(ARGV[2])\n" +
                "local current = tonumber(redis.call('get', key) or '0')\n" +
                "if current >= limit then\n" +
                "    return 0\n" +
                "end\n" +
                "current = redis.call('incr', key)\n" +
                "if current == 1 then\n" +
                "    redis.call('expire', key, expire)\n" +
                "end\n" +
                "return 1"
        );
        script.setResultType(Long.class);
        return script;
    }

    /**
     * 防伪验证接口 IP 限流脚本（拦截器使用）
     * 返回 { allowed, ttl }
     */
    @Bean
    public DefaultRedisScript<List> verifyRateLimitScript() {
        return buildListScript("lua/verify_rate_limit.lua");
    }

    /**
     * 一物一码扫码次数原子累加脚本
     * 返回 { current, isFake }
     */
    @Bean
    public DefaultRedisScript<List> scanCountScript() {
        return buildListScript("lua/scan_count.lua");
    }

    private DefaultRedisScript<List> buildListScript(String location) {
        DefaultRedisScript<List> script = new DefaultRedisScript<>();
        script.setLocation(new ClassPathResource(location));
        script.setResultType(List.class);
        return script;
    }
}
