package com.trace.product.aspect;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.trace.product.annotation.AuditLog;
import com.trace.product.common.BusinessException;
import com.trace.product.common.Result;
import com.trace.product.entity.AdminOperationLog;
import com.trace.product.service.AuditLogService;
import com.trace.product.util.IpUtils;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.multipart.MultipartFile;

import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * 操作审计切面。
 * <p>
 * 只记录「谁在什么时间调了什么接口、传了什么参数、成功还是失败、耗时多少」，
 * 不做 before/after 数据 diff（成本高且易出错）。
 * 写库走异步线程池，不阻塞业务响应。
 */
@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class AuditLogAspect {

    /** 密码类字段脱敏：JSON 里 "password":"xxx" 统一替换为 *** */
    private static final Pattern PASSWORD_PATTERN = Pattern.compile(
            "(\"(?:password|oldPassword|newPassword|pwd|secret|token)\"\\s*:\\s*\")[^\"]*\"",
            Pattern.CASE_INSENSITIVE);

    private static final int MAX_PARAM_LENGTH = 2000;

    private final ObjectMapper objectMapper;
    private final AuditLogService auditLogService;

    @Around("@annotation(auditLog)")
    public Object around(ProceedingJoinPoint pjp, AuditLog auditLog) throws Throwable {
        long start = System.currentTimeMillis();
        Object result = null;
        Throwable error = null;
        try {
            result = pjp.proceed();
            return result;
        } catch (Throwable t) {
            error = t;
            throw t;
        } finally {
            try {
                record(pjp, auditLog, result, error, System.currentTimeMillis() - start);
            } catch (Exception e) {
                // 审计失败绝不影响主流程
                log.warn("写入操作审计日志失败: {}", e.getMessage());
            }
        }
    }

    private void record(ProceedingJoinPoint pjp, AuditLog auditLog,
                        Object result, Throwable error, long costMs) {
        MethodSignature signature = (MethodSignature) pjp.getSignature();
        Method method = signature.getMethod();

        AdminOperationLog entity = new AdminOperationLog();
        entity.setModule(auditLog.module());
        entity.setAction(auditLog.action());
        entity.setMethod(method.getDeclaringClass().getSimpleName() + "." + method.getName());
        entity.setTargetId(extractTargetId(method, pjp.getArgs()));
        entity.setRequestParams(serializeArgs(pjp.getArgs()));
        entity.setCostMs((int) costMs);
        entity.setCreatedAt(LocalDateTime.now());

        ServletRequestAttributes attrs =
                (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attrs != null) {
            HttpServletRequest request = attrs.getRequest();
            entity.setRequestUri(request.getRequestURI());
            entity.setHttpMethod(request.getMethod());
            entity.setIp(IpUtils.getClientIp(request));
            Object adminId = request.getAttribute("adminId");
            if (adminId instanceof Long id) {
                entity.setAdminId(id);
            }
        }

        if (error != null) {
            // 业务异常沿用其业务码，系统异常记为 500
            if (error instanceof BusinessException be && be.getCode() != null) {
                entity.setResponseCode(be.getCode());
            } else {
                entity.setResponseCode(500);
            }
            entity.setErrorMsg(truncate(error.getClass().getSimpleName() + ": " + error.getMessage(), 500));
        } else if (result instanceof Result<?> r) {
            entity.setResponseCode(r.getCode());
            if (r.getCode() != null && r.getCode() != 200) {
                entity.setErrorMsg(truncate(r.getMessage(), 500));
            }
        } else {
            entity.setResponseCode(200);
        }

        auditLogService.saveAsync(entity);
    }

    /**
     * 优先取 @PathVariable 的值；没有路径变量时退化为 URI 最后一段中的纯数字 ID
     */
    private String extractTargetId(Method method, Object[] args) {
        Parameter[] params = method.getParameters();
        for (int i = 0; i < params.length && i < args.length; i++) {
            if (params[i].isAnnotationPresent(PathVariable.class) && args[i] != null) {
                return String.valueOf(args[i]);
            }
        }
        ServletRequestAttributes attrs =
                (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attrs != null) {
            String uri = attrs.getRequest().getRequestURI();
            String last = uri.substring(uri.lastIndexOf('/') + 1);
            if (last.matches("\\d{1,19}")) {
                return last;
            }
        }
        return null;
    }

    /** 序列化入参，过滤掉 Servlet 对象与文件流，密码字段脱敏 */
    private String serializeArgs(Object[] args) {
        List<Object> readable = new ArrayList<>();
        for (Object arg : args) {
            if (arg == null) {
                continue;
            }
            if (arg instanceof ServletRequest || arg instanceof ServletResponse
                    || arg instanceof MultipartFile || arg instanceof MultipartFile[]) {
                continue;
            }
            readable.add(arg);
        }
        if (readable.isEmpty()) {
            return null;
        }
        try {
            String json = objectMapper.writeValueAsString(readable.size() == 1 ? readable.get(0) : readable);
            json = PASSWORD_PATTERN.matcher(json).replaceAll("$1***\"");
            return truncate(json, MAX_PARAM_LENGTH);
        } catch (Exception e) {
            return "[参数序列化失败: " + e.getClass().getSimpleName() + "]";
        }
    }

    private String truncate(String text, int max) {
        if (text == null) {
            return null;
        }
        return text.length() <= max ? text : text.substring(0, max) + "...";
    }
}
