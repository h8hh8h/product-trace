package com.trace.product.service;

import cn.hutool.core.util.IdUtil;
import cn.hutool.crypto.digest.BCrypt;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.trace.product.common.BusinessException;
import com.trace.product.common.ResultCode;
import com.trace.product.dto.LoginDTO;
import com.trace.product.dto.LoginVO;
import com.trace.product.entity.SysAdmin;
import com.trace.product.mapper.SysAdminMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;

/**
 * 后台登录认证（Token 存 Redis，有效期 12 小时）
 * <p>
 * 安全策略：
 *   - 登录失败计数：同一用户名连续失败 maxFail 次后锁定 lockMinutes 分钟（用用户名而非 IP 做 key，防代理绕过）
 *   - 密码复杂度：长度 ≥ minLength，且必须同时包含字母和数字
 *   - 首登强制改密：must_change_password=1 时只放行 /admin/auth/** 接口
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private static final String TOKEN_KEY_PREFIX = "trace:admin:token:";
    private static final String LOGIN_FAIL_PREFIX = "trace:login:fail:";
    private static final String LOGIN_LOCK_PREFIX = "trace:login:lock:";
    private static final long TOKEN_TTL_HOURS = 12;

    private final SysAdminMapper sysAdminMapper;
    private final StringRedisTemplate redisTemplate;

    /** 连续失败多少次后锁定 */
    @Value("${trace.security.login.max-fail:5}")
    private int maxFail;

    /** 锁定时长（分钟） */
    @Value("${trace.security.login.lock-minutes:30}")
    private int lockMinutes;

    /** 密码最小长度 */
    @Value("${trace.security.password.min-length:8}")
    private int passwordMinLength;

    public LoginVO login(LoginDTO dto, String ip) {
        String username = dto.getUsername();

        // 1. 锁定检查
        String lockKey = LOGIN_LOCK_PREFIX + username;
        String lockTtl = redisTemplate.opsForValue().get(lockKey);
        if (lockTtl != null) {
            long remain = redisTemplate.getExpire(lockKey, TimeUnit.MINUTES);
            throw new BusinessException(ResultCode.LOGIN_LOCKED.getCode(),
                    "账号已被锁定，请 " + Math.max(remain, 1) + " 分钟后再试");
        }

        // 2. 凭证校验
        SysAdmin admin = sysAdminMapper.selectOne(
                new LambdaQueryWrapper<SysAdmin>().eq(SysAdmin::getUsername, username));
        if (admin == null || !BCrypt.checkpw(dto.getPassword(), admin.getPassword())) {
            onLoginFailed(username, ip);
            throw new BusinessException("用户名或密码错误");
        }
        if (admin.getStatus() == null || admin.getStatus() != 1) {
            throw new BusinessException("账号已被禁用");
        }

        // 3. 登录成功：清失败计数
        redisTemplate.delete(LOGIN_FAIL_PREFIX + username);

        String token = IdUtil.simpleUUID();
        redisTemplate.opsForValue().set(TOKEN_KEY_PREFIX + token,
                String.valueOf(admin.getId()), TOKEN_TTL_HOURS, TimeUnit.HOURS);

        admin.setLastLoginTime(LocalDateTime.now());
        admin.setLastLoginIp(ip);
        sysAdminMapper.updateById(admin);

        boolean mustChange = admin.getMustChangePassword() != null && admin.getMustChangePassword() == 1;
        return new LoginVO(token, admin.getUsername(), admin.getNickname(), admin.getRole(), mustChange);
    }

    /** 失败计数 +1，达到阈值则锁定账号 */
    private void onLoginFailed(String username, String ip) {
        String failKey = LOGIN_FAIL_PREFIX + username;
        Long fails = redisTemplate.opsForValue().increment(failKey);
        if (fails != null && fails == 1) {
            // 失败计数窗口 30 分钟，成功登录会清零
            redisTemplate.expire(failKey, 30, TimeUnit.MINUTES);
        }
        if (fails != null && fails >= maxFail) {
            redisTemplate.opsForValue().set(LOGIN_LOCK_PREFIX + username,
                    "1", lockMinutes, TimeUnit.MINUTES);
            redisTemplate.delete(failKey);
            log.warn("账号因连续登录失败被锁定: username={}, ip={}, fails={}, lockMinutes={}",
                    username, ip, fails, lockMinutes);
        } else {
            log.warn("登录失败: username={}, ip={}, 累计失败 {} 次", username, ip, fails);
        }
    }

    /** 校验 token，有效则续期并返回管理员 ID */
    public Long verifyToken(String token) {
        if (token == null || token.isBlank()) {
            return null;
        }
        String key = TOKEN_KEY_PREFIX + token;
        String adminId = redisTemplate.opsForValue().get(key);
        if (adminId != null) {
            redisTemplate.expire(key, TOKEN_TTL_HOURS, TimeUnit.HOURS);
            return Long.valueOf(adminId);
        }
        return null;
    }

    public SysAdmin getAdminInfo(Long adminId) {
        return sysAdminMapper.selectById(adminId);
    }

    /** 该管理员是否处于「必须先改密码」状态 */
    public boolean mustChangePassword(Long adminId) {
        SysAdmin admin = sysAdminMapper.selectById(adminId);
        return admin != null && admin.getMustChangePassword() != null && admin.getMustChangePassword() == 1;
    }

    public void logout(String token) {
        if (token != null && !token.isBlank()) {
            redisTemplate.delete(TOKEN_KEY_PREFIX + token);
        }
    }

    /** 修改密码（含复杂度校验 + 清除首登强制改密标记） */
    public void changePassword(Long adminId, String oldPassword, String newPassword) {
        SysAdmin admin = sysAdminMapper.selectById(adminId);
        if (admin == null || !BCrypt.checkpw(oldPassword, admin.getPassword())) {
            throw new BusinessException("原密码错误");
        }
        validatePassword(newPassword);
        if (BCrypt.checkpw(newPassword, admin.getPassword())) {
            throw new BusinessException("新密码不能与原密码相同");
        }
        admin.setPassword(BCrypt.hashpw(newPassword));
        admin.setMustChangePassword(0);
        admin.setPasswordUpdateTime(LocalDateTime.now());
        sysAdminMapper.updateById(admin);
        log.info("管理员修改密码成功: adminId={}, username={}", adminId, admin.getUsername());
    }

    /** 密码复杂度校验：长度 + 字母 + 数字 */
    public void validatePassword(String password) {
        if (password == null || password.length() < passwordMinLength) {
            throw new BusinessException("密码长度不能少于 " + passwordMinLength + " 位");
        }
        boolean hasLetter = password.chars().anyMatch(Character::isLetter);
        boolean hasDigit = password.chars().anyMatch(Character::isDigit);
        if (!hasLetter || !hasDigit) {
            throw new BusinessException("密码必须同时包含字母和数字");
        }
    }
}
