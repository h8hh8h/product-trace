package com.trace.product.interceptor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.trace.product.annotation.RequirePerm;
import com.trace.product.common.Result;
import com.trace.product.common.ResultCode;
import com.trace.product.entity.SysAdmin;
import com.trace.product.mapper.SysAdminMapper;
import com.trace.product.service.RoleService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * 后台接口模块权限拦截器（RBAC）
 * <p>
 * 只处理 HandlerMethod 且方法/类上标注了 @RequirePerm 的接口，未标注接口直接放行。
 * sys_admin.role_id 为 NULL 视为超级管理员，直接放行；
 * 否则校验角色 permissions（JSON 数组）是否包含注解声明的模块 key，不含则返回 403 业务码。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PermissionInterceptor implements HandlerInterceptor {

    private final SysAdminMapper sysAdminMapper;
    private final RoleService roleService;
    private final ObjectMapper objectMapper;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        if (!(handler instanceof HandlerMethod handlerMethod)) {
            return true;
        }
        RequirePerm requirePerm = handlerMethod.getMethodAnnotation(RequirePerm.class);
        if (requirePerm == null) {
            requirePerm = handlerMethod.getBeanType().getAnnotation(RequirePerm.class);
        }
        if (requirePerm == null) {
            return true;
        }

        Object attr = request.getAttribute("adminId");
        if (!(attr instanceof Long adminId)) {
            return true;
        }
        SysAdmin admin = sysAdminMapper.selectById(adminId);
        if (admin == null || admin.getRoleId() == null) {
            // 超级管理员
            return true;
        }
        List<String> perms = roleService.getPermissions(admin.getRoleId());
        if (perms.contains(requirePerm.value())) {
            return true;
        }
        log.warn("权限校验未通过：username={}, roleId={}, 需要={}, 拥有={}, 请求={} {}",
                admin.getUsername(), admin.getRoleId(), requirePerm.value(), perms,
                request.getMethod(), request.getRequestURI());

        response.setStatus(HttpServletResponse.SC_OK);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write(objectMapper.writeValueAsString(
                Result.fail(ResultCode.FORBIDDEN.getCode(), "无权限操作")));
        return false;
    }
}
