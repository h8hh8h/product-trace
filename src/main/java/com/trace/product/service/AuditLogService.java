package com.trace.product.service;

import com.trace.product.entity.AdminOperationLog;
import com.trace.product.entity.SysAdmin;
import com.trace.product.mapper.AdminOperationLogMapper;
import com.trace.product.mapper.SysAdminMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * 操作审计日志写入服务。
 * 异步执行，避免审计成为业务响应瓶颈。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuditLogService {

    private final AdminOperationLogMapper adminOperationLogMapper;
    private final SysAdminMapper sysAdminMapper;

    @Async("taskExecutor")
    public void saveAsync(AdminOperationLog entity) {
        try {
            if (entity.getAdminId() != null && entity.getAdminUsername() == null) {
                SysAdmin admin = sysAdminMapper.selectById(entity.getAdminId());
                if (admin != null) {
                    entity.setAdminUsername(admin.getUsername());
                }
            }
            adminOperationLogMapper.insert(entity);
        } catch (Exception e) {
            log.warn("审计日志落库失败: module={}, action={}, err={}",
                    entity.getModule(), entity.getAction(), e.getMessage());
        }
    }
}
