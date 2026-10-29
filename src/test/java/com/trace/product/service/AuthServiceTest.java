package com.trace.product.service;

import cn.hutool.crypto.digest.BCrypt;
import com.trace.product.common.BusinessException;
import com.trace.product.common.ResultCode;
import com.trace.product.dto.LoginDTO;
import com.trace.product.dto.LoginVO;
import com.trace.product.entity.SysAdmin;
import com.trace.product.mapper.SysAdminMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * AuthService 单测：mock SysAdminMapper + StringRedisTemplate，不启动 Spring 容器。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AuthServiceTest {

    private static final String LOCK_KEY = "trace:login:lock:admin";
    private static final String FAIL_KEY = "trace:login:fail:admin";

    @Mock
    private SysAdminMapper sysAdminMapper;
    @Mock
    private StringRedisTemplate redisTemplate;
    @Mock
    private ValueOperations<String, String> valueOps;

    private AuthService authService;

    @BeforeEach
    void setUp() {
        authService = new AuthService(sysAdminMapper, redisTemplate);
        ReflectionTestUtils.setField(authService, "maxFail", 5);
        ReflectionTestUtils.setField(authService, "lockMinutes", 30);
        ReflectionTestUtils.setField(authService, "passwordMinLength", 8);
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
    }

    private LoginDTO dto(String username, String password) {
        LoginDTO dto = new LoginDTO();
        dto.setUsername(username);
        dto.setPassword(password);
        return dto;
    }

    @Test
    void loginThrowsLockedExceptionWhenAccountLocked() {
        when(valueOps.get(LOCK_KEY)).thenReturn("1");
        when(redisTemplate.getExpire(LOCK_KEY, TimeUnit.MINUTES)).thenReturn(25L);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> authService.login(dto("admin", "whatever"), "127.0.0.1"));
        assertEquals(ResultCode.LOGIN_LOCKED.getCode(), ex.getCode());
        assertTrue(ex.getMessage().contains("锁定"));
        // 锁定期间不应再查库
        verify(sysAdminMapper, never()).selectOne(any());
    }

    @Test
    void loginFailureAtMaxFailLocksAccount() {
        // 用户不存在 → 凭证校验失败；本次失败恰好达到上限
        when(valueOps.increment(FAIL_KEY)).thenReturn(5L);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> authService.login(dto("admin", "bad-pass"), "127.0.0.1"));
        assertEquals("用户名或密码错误", ex.getMessage());

        // 达到上限：写入锁定标记并清除失败计数
        verify(valueOps).set(eq(LOCK_KEY), eq("1"), eq(30L), eq(TimeUnit.MINUTES));
        verify(redisTemplate).delete(FAIL_KEY);
    }

    @Test
    void loginFailureBelowThresholdOnlySetsExpire() {
        when(valueOps.increment(FAIL_KEY)).thenReturn(1L);

        assertThrows(BusinessException.class,
                () -> authService.login(dto("admin", "bad-pass"), "127.0.0.1"));

        // 首次失败：设置 30 分钟计数窗口，不锁定
        verify(redisTemplate).expire(FAIL_KEY, 30L, TimeUnit.MINUTES);
        verify(valueOps, never()).set(startsWith("trace:login:lock:"), any(), anyLong(), any());
    }

    @Test
    void loginFailureWithWrongPasswordIncrementsCounter() {
        SysAdmin admin = new SysAdmin();
        admin.setId(1L);
        admin.setUsername("admin");
        admin.setPassword(BCrypt.hashpw("RealPass123"));
        admin.setStatus(1);
        when(sysAdminMapper.selectOne(any())).thenReturn(admin);
        when(valueOps.increment(FAIL_KEY)).thenReturn(2L);

        assertThrows(BusinessException.class,
                () -> authService.login(dto("admin", "WrongPass123"), "127.0.0.1"));
        verify(valueOps).increment(FAIL_KEY);
    }

    @Test
    void loginSuccessReturnsTokenAndClearsFailCount() {
        SysAdmin admin = new SysAdmin();
        admin.setId(7L);
        admin.setUsername("admin");
        admin.setNickname("超管");
        admin.setRole("ADMIN");
        admin.setPassword(BCrypt.hashpw("Passw0rd1"));
        admin.setStatus(1);
        admin.setMustChangePassword(0);
        when(sysAdminMapper.selectOne(any())).thenReturn(admin);

        LoginVO vo = authService.login(dto("admin", "Passw0rd1"), "127.0.0.1");

        assertNotNull(vo.getToken());
        assertFalse(vo.getToken().isBlank());
        assertEquals("admin", vo.getUsername());
        assertEquals("超管", vo.getNickname());
        assertEquals(Boolean.FALSE, vo.getMustChangePassword());

        // 登录成功：清失败计数、token 写 Redis（12 小时）、更新最后登录信息
        verify(redisTemplate).delete(FAIL_KEY);
        verify(valueOps).set(startsWith("trace:admin:token:"), eq("7"), eq(12L), eq(TimeUnit.HOURS));
        verify(sysAdminMapper).updateById(admin);
    }

    @Test
    void validatePasswordRejectsPureLetters() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> authService.validatePassword("abcdefgh"));
        assertTrue(ex.getMessage().contains("字母和数字"));
    }

    @Test
    void validatePasswordRejectsPureDigits() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> authService.validatePassword("12345678"));
        assertTrue(ex.getMessage().contains("字母和数字"));
    }

    @Test
    void validatePasswordRejectsTooShort() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> authService.validatePassword("Ab1"));
        assertTrue(ex.getMessage().contains("8"));
    }

    @Test
    void validatePasswordAcceptsValidPassword() {
        assertDoesNotThrow(() -> authService.validatePassword("Abcdef12"));
        assertDoesNotThrow(() -> authService.validatePassword("pass1234"));
    }
}
