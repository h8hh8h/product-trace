package com.trace.product.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.trace.product.annotation.RequirePerm;
import com.trace.product.common.Result;
import com.trace.product.entity.AdminOperationLog;
import com.trace.product.mapper.AdminOperationLogMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 后台操作审计日志查询接口
 */
@RequirePerm("audit")
@RestController
@RequestMapping("/admin/audit-log")
@RequiredArgsConstructor
public class AuditLogController {

    private final AdminOperationLogMapper adminOperationLogMapper;

    /**
     * 分页查询审计日志
     */
    @GetMapping("/page")
    public Result<Page<AdminOperationLog>> page(
            @RequestParam(defaultValue = "1") long current,
            @RequestParam(defaultValue = "20") long size,
            @RequestParam(required = false) String module,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String operator,
            @RequestParam(required = false) Integer success,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {

        LambdaQueryWrapper<AdminOperationLog> qw = new LambdaQueryWrapper<>();
        if (module != null && !module.isBlank()) {
            qw.eq(AdminOperationLog::getModule, module);
        }
        if (action != null && !action.isBlank()) {
            qw.eq(AdminOperationLog::getAction, action);
        }
        if (operator != null && !operator.isBlank()) {
            qw.like(AdminOperationLog::getAdminUsername, operator.trim());
        }
        if (success != null) {
            if (success == 1) {
                qw.eq(AdminOperationLog::getResponseCode, 200);
            } else {
                qw.ne(AdminOperationLog::getResponseCode, 200);
            }
        }
        if (startDate != null) {
            qw.ge(AdminOperationLog::getCreatedAt, startDate.atStartOfDay());
        }
        if (endDate != null) {
            qw.le(AdminOperationLog::getCreatedAt, LocalDateTime.of(endDate, LocalTime.MAX));
        }
        qw.orderByDesc(AdminOperationLog::getId);
        return Result.success(adminOperationLogMapper.selectPage(new Page<>(current, size), qw));
    }

    /**
     * 筛选下拉项：已有模块/动作列表
     */
    @GetMapping("/options")
    public Result<Map<String, List<String>>> options() {
        List<AdminOperationLog> all = adminOperationLogMapper.selectList(
                new LambdaQueryWrapper<AdminOperationLog>()
                        .select(AdminOperationLog::getModule, AdminOperationLog::getAction)
                        .orderByAsc(AdminOperationLog::getModule));
        List<String> modules = all.stream().map(AdminOperationLog::getModule)
                .filter(m -> m != null && !m.isBlank()).distinct().sorted().toList();
        List<String> actions = all.stream().map(AdminOperationLog::getAction)
                .filter(a -> a != null && !a.isBlank()).distinct().sorted().toList();
        Map<String, List<String>> data = new LinkedHashMap<>();
        data.put("modules", modules);
        data.put("actions", actions);
        return Result.success(data);
    }
}
