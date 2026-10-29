package com.trace.product.controller;

import cn.hutool.crypto.digest.BCrypt;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.trace.product.annotation.AuditLog;
import com.trace.product.annotation.RequirePerm;
import com.trace.product.common.BusinessException;
import com.trace.product.common.Result;
import com.trace.product.common.ResultCode;
import com.trace.product.entity.SysAdmin;
import com.trace.product.entity.SysRole;
import com.trace.product.mapper.SysAdminMapper;
import com.trace.product.mapper.SysRoleMapper;
import com.trace.product.service.AuthService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 后台管理员账号管理接口（RBAC）
 */
@RequirePerm("system")
@RestController
@RequestMapping("/admin/admin-user")
@RequiredArgsConstructor
public class AdminUserController {

    private final SysAdminMapper sysAdminMapper;
    private final SysRoleMapper sysRoleMapper;
    private final AuthService authService;

    /**
     * 管理员分页列表（联 sys_role 查角色名，不返回密码）
     */
    @GetMapping("/page")
    public Result<Page<SysAdmin>> page(@RequestParam(defaultValue = "1") long current,
                                       @RequestParam(defaultValue = "20") long size,
                                       @RequestParam(required = false) String username) {
        LambdaQueryWrapper<SysAdmin> qw = new LambdaQueryWrapper<>();
        if (username != null && !username.isBlank()) {
            qw.like(SysAdmin::getUsername, username.trim());
        }
        qw.orderByAsc(SysAdmin::getId);
        Page<SysAdmin> page = sysAdminMapper.selectPage(new Page<>(current, size), qw);

        Map<Long, String> roleNames = new HashMap<>();
        for (SysRole role : sysRoleMapper.selectList(null)) {
            // 手工补建的角色表 role_name 可能为 NULL，toMap 遇 null value 会抛 NPE
            roleNames.put(role.getId(), role.getRoleName() == null ? "-" : role.getRoleName());
        }
        List<SysAdmin> records = page.getRecords();
        for (SysAdmin admin : records) {
            admin.setPassword(null);
            admin.setRoleName(admin.getRoleId() == null ? "超级管理员"
                    : roleNames.getOrDefault(admin.getRoleId(), "-"));
        }
        return Result.success(page);
    }

    /**
     * 新增管理员（用户名唯一 + 初始密码复杂度校验，首登强制改密）
     */
    @AuditLog(module = "system", action = "新增管理员")
    @PostMapping
    public Result<SysAdmin> save(@Valid @RequestBody AdminSaveDTO dto) {
        Long exists = sysAdminMapper.selectCount(new LambdaQueryWrapper<SysAdmin>()
                .eq(SysAdmin::getUsername, dto.getUsername().trim()));
        if (exists != null && exists > 0) {
            throw new BusinessException("用户名已存在：" + dto.getUsername());
        }
        validatePassword(dto.getPassword());
        checkRoleExists(dto.getRoleId());

        SysAdmin admin = new SysAdmin();
        admin.setUsername(dto.getUsername().trim());
        admin.setPassword(BCrypt.hashpw(dto.getPassword()));
        admin.setNickname(dto.getNickname() == null || dto.getNickname().isBlank()
                ? dto.getUsername().trim() : dto.getNickname().trim());
        admin.setRole("OPERATOR");
        admin.setRoleId(dto.getRoleId());
        admin.setStatus(1);
        admin.setMustChangePassword(1);
        admin.setPasswordUpdateTime(LocalDateTime.now());
        sysAdminMapper.insert(admin);
        admin.setPassword(null);
        return Result.success(admin);
    }

    /**
     * 重置密码（重置后首登强制改密）
     */
    @AuditLog(module = "system", action = "重置密码")
    @PutMapping("/{id}/password")
    public Result<Void> resetPassword(@PathVariable Long id, @Valid @RequestBody PasswordDTO dto) {
        SysAdmin admin = sysAdminMapper.selectById(id);
        if (admin == null) {
            throw new BusinessException(ResultCode.NOT_FOUND.getCode(), "管理员不存在");
        }
        validatePassword(dto.getPassword());
        admin.setPassword(BCrypt.hashpw(dto.getPassword()));
        admin.setMustChangePassword(1);
        admin.setPasswordUpdateTime(LocalDateTime.now());
        sysAdminMapper.updateById(admin);
        return Result.success();
    }

    /**
     * 分配角色（roleId 传 null 表示设为超级管理员）
     */
    @AuditLog(module = "system", action = "分配角色")
    @PutMapping("/{id}/role")
    public Result<Void> assignRole(@PathVariable Long id, @RequestBody AssignRoleDTO dto) {
        SysAdmin admin = sysAdminMapper.selectById(id);
        if (admin == null) {
            throw new BusinessException(ResultCode.NOT_FOUND.getCode(), "管理员不存在");
        }
        checkRoleExists(dto.getRoleId());
        admin.setRoleId(dto.getRoleId());
        sysAdminMapper.updateById(admin);
        return Result.success();
    }

    /** 密码复杂度与 AuthService 保持一致，弱密码返回 PASSWORD_TOO_WEAK */
    private void validatePassword(String password) {
        try {
            authService.validatePassword(password);
        } catch (BusinessException e) {
            throw new BusinessException(ResultCode.PASSWORD_TOO_WEAK.getCode(), e.getMessage());
        }
    }

    private void checkRoleExists(Long roleId) {
        if (roleId == null) {
            return;
        }
        SysRole role = sysRoleMapper.selectById(roleId);
        if (role == null) {
            throw new BusinessException("角色不存在");
        }
    }

    @Data
    public static class AdminSaveDTO {
        @NotBlank(message = "用户名不能为空")
        private String username;
        @NotBlank(message = "初始密码不能为空")
        private String password;
        private String nickname;
        private Long roleId;
    }

    @Data
    public static class PasswordDTO {
        @NotBlank(message = "新密码不能为空")
        private String password;
    }

    @Data
    public static class AssignRoleDTO {
        private Long roleId;
    }
}
