package com.trace.product.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 登录返回
 */
@Data
@AllArgsConstructor
public class LoginVO {

    private String token;

    private String username;

    private String nickname;

    private String role;

    /** true 表示必须先修改初始密码，前端应强制弹出改密窗口 */
    private Boolean mustChangePassword;
}
