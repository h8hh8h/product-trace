package com.trace.product.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 后台管理员
 */
@Data
@TableName("sys_admin")
public class SysAdmin {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String username;

    private String password;

    private String nickname;

    private String avatar;

    /** 角色：ADMIN/OPERATOR */
    private String role;

    /** 角色ID，NULL为超级管理员 */
    private Long roleId;

    /** 角色名称（联查 sys_role，非表字段） */
    @TableField(exist = false)
    private String roleName;

    /** 状态：1-启用 0-禁用 */
    private Integer status;

    /** 1-必须先修改初始密码（改密前除 /admin/auth/** 外的接口一律 403） */
    private Integer mustChangePassword;

    /** 最后一次修改密码时间，用于定期强制更换策略 */
    private LocalDateTime passwordUpdateTime;

    private LocalDateTime lastLoginTime;

    private String lastLoginIp;

    @TableLogic
    private Integer deleted;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
