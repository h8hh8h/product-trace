package com.trace.product.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.trace.product.common.BusinessException;
import com.trace.product.dto.BatchTaskProgress;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * 批量生成任务进度存储（Redis JSON）
 */
@Component
@RequiredArgsConstructor
public class BatchProgressStore {

    private static final String KEY_PREFIX = "trace:batch:";

    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;

    @Value("${trace.batch.progress-ttl:86400}")
    private long progressTtl;

    public void save(BatchTaskProgress progress) {
        try {
            String json = objectMapper.writeValueAsString(progress);
            stringRedisTemplate.opsForValue()
                    .set(KEY_PREFIX + progress.getTaskId(), json, Duration.ofSeconds(progressTtl));
        } catch (Exception e) {
            throw new BusinessException("保存批量任务进度失败: " + e.getMessage());
        }
    }

    public BatchTaskProgress get(String taskId) {
        String json = stringRedisTemplate.opsForValue().get(KEY_PREFIX + taskId);
        if (json == null) {
            return null;
        }
        try {
            return objectMapper.readValue(json, BatchTaskProgress.class);
        } catch (Exception e) {
            throw new BusinessException("读取批量任务进度失败: " + e.getMessage());
        }
    }
}
