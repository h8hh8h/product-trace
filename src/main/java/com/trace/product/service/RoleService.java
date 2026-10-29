package com.trace.product.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.trace.product.common.BusinessException;
import com.trace.product.common.ResultCode;
import com.trace.product.entity.SysAdmin;
import com.trace.product.entity.SysRole;
import com.trace.product.mapper.SysAdminMapper;
import com.trace.product.mapper.SysRoleMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 后台角色管理（RBAC）
 * <p>
 * 角色权限按 roleId 缓存到 Redis（trace:role:perms:{roleId}，JSON 数组，10 分钟过期），
 * 角色更新/删除时清除缓存。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RoleService {

    /** 全部模块 key（超级管理员的权限） */
    public static final List<String> ALL_MODULES = List.of(
            "product", "code", "stats", "config", "audit", "media", "system");

    private static final String PERMS_KEY_PREFIX = "trace:role:perms:";
    private static final long PERMS_TTL_MINUTES = 10;

    private final SysRoleMapper sysRoleMapper;
    private final SysAdminMapper sysAdminMapper;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public Page<SysRole> page(long current, long size, String roleName) {
        LambdaQueryWrapper<SysRole> qw = new LambdaQueryWrapper<>();
        if (roleName != null && !roleName.isBlank()) {
            qw.like(SysRole::getRoleName, roleName.trim());
        }
        qw.orderByAsc(SysRole::getId);
        return sysRoleMapper.selectPage(new Page<>(current, size), qw);
    }

    /** 全部启用角色（下拉用） */
    public List<SysRole> listEnabled() {
        return sysRoleMapper.selectList(new LambdaQueryWrapper<SysRole>()
                .eq(SysRole::getStatus, 1)
                .orderByAsc(SysRole::getId));
    }

    public SysRole create(SysRole role) {
        role.setId(null);
        checkRoleCodeUnique(role.getRoleCode(), null);
        if (role.getStatus() == null) {
            role.setStatus(1);
        }
        sysRoleMapper.insert(role);
        return role;
    }

    public SysRole update(SysRole role) {
        if (role.getId() == null || sysRoleMapper.selectById(role.getId()) == null) {
            throw new BusinessException(ResultCode.NOT_FOUND.getCode(), "角色不存在");
        }
        checkRoleCodeUnique(role.getRoleCode(), role.getId());
        sysRoleMapper.updateById(role);
        evictPermsCache(role.getId());
        return role;
    }

    public void delete(Long id) {
        SysRole role = sysRoleMapper.selectById(id);
        if (role == null) {
            throw new BusinessException(ResultCode.NOT_FOUND.getCode(), "角色不存在");
        }
        Long refs = sysAdminMapper.selectCount(new LambdaQueryWrapper<SysAdmin>()
                .eq(SysAdmin::getRoleId, id));
        if (refs != null && refs > 0) {
            throw new BusinessException("该角色已分配给 " + refs + " 个管理员，无法删除");
        }
        sysRoleMapper.deleteById(id);
        evictPermsCache(id);
    }

    /**
     * 取角色权限模块 key 列表（带 Redis 缓存）。
     * 角色不存在/禁用/权限为空时返回空列表。
     */
    public List<String> getPermissions(Long roleId) {
        String key = PERMS_KEY_PREFIX + roleId;
        String cached = redisTemplate.opsForValue().get(key);
        if (cached != null) {
            try {
                return objectMapper.readValue(cached, new TypeReference<List<String>>() {});
            } catch (Exception e) {
                log.warn("角色权限缓存解析失败，回源查询: roleId={}", roleId);
            }
        }
        List<String> perms = loadPermissions(roleId);
        try {
            redisTemplate.opsForValue().set(key, objectMapper.writeValueAsString(perms),
                    PERMS_TTL_MINUTES, TimeUnit.MINUTES);
        } catch (Exception e) {
            log.warn("角色权限缓存写入失败: roleId={}, {}", roleId, e.getMessage());
        }
        return perms;
    }

    private List<String> loadPermissions(Long roleId) {
        SysRole role = sysRoleMapper.selectById(roleId);
        if (role == null || role.getStatus() == null || role.getStatus() != 1
                || role.getPermissions() == null || role.getPermissions().isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(role.getPermissions(), new TypeReference<List<String>>() {});
        } catch (Exception e) {
            log.warn("角色权限 JSON 解析失败: roleId={}, permissions={}", roleId, role.getPermissions());
            return List.of();
        }
    }

    private void evictPermsCache(Long roleId) {
        redisTemplate.delete(PERMS_KEY_PREFIX + roleId);
    }

    private void checkRoleCodeUnique(String roleCode, Long excludeId) {
        if (roleCode == null || roleCode.isBlank()) {
            throw new BusinessException("角色编码不能为空");
        }
        LambdaQueryWrapper<SysRole> qw = new LambdaQueryWrapper<SysRole>()
                .eq(SysRole::getRoleCode, roleCode.trim());
        if (excludeId != null) {
            qw.ne(SysRole::getId, excludeId);
        }
        Long exists = sysRoleMapper.selectCount(qw);
        if (exists != null && exists > 0) {
            throw new BusinessException("角色编码已存在：" + roleCode);
        }
    }
}
