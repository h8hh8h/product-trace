package com.trace.product.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.trace.product.annotation.AuditLog;
import com.trace.product.annotation.RequirePerm;
import com.trace.product.common.Result;
import com.trace.product.entity.SysRole;
import com.trace.product.service.RoleService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 后台角色管理接口（RBAC）
 */
@RequirePerm("system")
@RestController
@RequestMapping("/admin/role")
@RequiredArgsConstructor
public class RoleController {

    private final RoleService roleService;

    /**
     * 角色分页（role_name 模糊）
     */
    @GetMapping("/page")
    public Result<Page<SysRole>> page(@RequestParam(defaultValue = "1") long current,
                                      @RequestParam(defaultValue = "20") long size,
                                      @RequestParam(required = false) String roleName) {
        return Result.success(roleService.page(current, size, roleName));
    }

    /**
     * 全部启用角色（下拉用）
     */
    @GetMapping("/list")
    public Result<List<SysRole>> list() {
        return Result.success(roleService.listEnabled());
    }

    /**
     * 新增角色
     */
    @AuditLog(module = "system", action = "新增角色")
    @PostMapping
    public Result<SysRole> save(@RequestBody SysRole role) {
        return Result.success(roleService.create(role));
    }

    /**
     * 编辑角色
     */
    @AuditLog(module = "system", action = "编辑角色")
    @PutMapping
    public Result<SysRole> update(@RequestBody SysRole role) {
        return Result.success(roleService.update(role));
    }

    /**
     * 删除角色（已被管理员引用时拒绝）
     */
    @AuditLog(module = "system", action = "删除角色")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        roleService.delete(id);
        return Result.success();
    }
}
