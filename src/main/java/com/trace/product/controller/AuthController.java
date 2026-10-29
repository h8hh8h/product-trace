package com.trace.product.controller;

import com.trace.product.annotation.AuditLog;
import com.trace.product.common.Result;
import com.trace.product.dto.LoginDTO;
import com.trace.product.dto.LoginVO;
import com.trace.product.entity.SysAdmin;
import com.trace.product.service.AuthService;
import com.trace.product.service.RoleService;
import com.trace.product.util.IpUtils;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 后台登录认证接口
 */
@RestController
@RequestMapping("/admin/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final RoleService roleService;

    @PostMapping("/login")
    public Result<LoginVO> login(@Valid @RequestBody LoginDTO dto, HttpServletRequest request) {
        return Result.success(authService.login(dto, IpUtils.getClientIp(request)));
    }

    @GetMapping("/info")
    public Result<Map<String, Object>> info(HttpServletRequest request) {
        Long adminId = (Long) request.getAttribute("adminId");
        SysAdmin admin = authService.getAdminInfo(adminId);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("username", admin.getUsername());
        data.put("nickname", admin.getNickname());
        data.put("role", admin.getRole());
        data.put("lastLoginTime", admin.getLastLoginTime());
        data.put("mustChangePassword", admin.getMustChangePassword() != null && admin.getMustChangePassword() == 1);
        data.put("roleId", admin.getRoleId());
        // 超级管理员（role_id 为 NULL）拥有全部模块权限
        data.put("permissions", admin.getRoleId() == null
                ? RoleService.ALL_MODULES : roleService.getPermissions(admin.getRoleId()));
        return Result.success(data);
    }

    @PostMapping("/logout")
    public Result<Void> logout(HttpServletRequest request) {
        authService.logout(resolveToken(request));
        return Result.success();
    }

    @AuditLog(module = "账号", action = "修改密码")
    @PostMapping("/password")
    public Result<Void> changePassword(@Valid @RequestBody PasswordDTO dto, HttpServletRequest request) {
        Long adminId = (Long) request.getAttribute("adminId");
        authService.changePassword(adminId, dto.getOldPassword(), dto.getNewPassword());
        return Result.success();
    }

    static String resolveToken(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            return header.substring(7);
        }
        return header;
    }

    @Data
    public static class PasswordDTO {
        @NotBlank(message = "原密码不能为空")
        private String oldPassword;
        @NotBlank(message = "新密码不能为空")
        private String newPassword;
    }
}
