package com.trace.product.interceptor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.trace.product.common.Result;
import com.trace.product.common.ResultCode;
import com.trace.product.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.nio.charset.StandardCharsets;

/**
 * 后台接口登录鉴权拦截器
 * <p>
 * 除登录鉴权外，还负责「首登强制改密」拦截：
 * must_change_password=1 时，只放行 /admin/auth/** 下的接口（info / password / logout），
 * 其余后台接口一律返回 403 + 业务码 2002，前端据此弹出改密窗口。
 */
@Component
@RequiredArgsConstructor
public class AuthInterceptor implements HandlerInterceptor {

    private final AuthService authService;
    private final ObjectMapper objectMapper;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        String header = request.getHeader("Authorization");
        String token = (header != null && header.startsWith("Bearer ")) ? header.substring(7) : header;
        Long adminId = authService.verifyToken(token);
        if (adminId == null) {
            return reject(response, 401, ResultCode.UNAUTHORIZED.getCode(), "未登录或登录已过期");
        }

        // 首登强制改密：除 /admin/auth/** 外全部拦截
        // 用 getServletPath() 而非 getRequestURI()，避免带上 context-path（/api）导致判断失效
        String path = request.getServletPath();
        if (!path.startsWith("/admin/auth/") && authService.mustChangePassword(adminId)) {
            return reject(response, 403, ResultCode.MUST_CHANGE_PASSWORD.getCode(),
                    "请先修改初始密码后再继续操作");
        }

        request.setAttribute("adminId", adminId);
        return true;
    }

    private boolean reject(HttpServletResponse response, int httpStatus, Integer bizCode, String message) throws Exception {
        response.setStatus(httpStatus);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write(objectMapper.writeValueAsString(Result.fail(bizCode, message)));
        return false;
    }
}
