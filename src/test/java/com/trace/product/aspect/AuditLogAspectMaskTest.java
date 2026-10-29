package com.trace.product.aspect;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.trace.product.dto.LoginDTO;
import com.trace.product.service.AuditLogService;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

/**
 * AuditLogAspect 入参序列化的密码脱敏单测。
 * serializeArgs 为 private，用反射调用；不触发切面逻辑、不依赖 Spring 容器。
 */
class AuditLogAspectMaskTest {

    private final AuditLogAspect aspect =
            new AuditLogAspect(new ObjectMapper(), mock(AuditLogService.class));

    private String serialize(Object arg) throws Exception {
        Method m = AuditLogAspect.class.getDeclaredMethod("serializeArgs", Object[].class);
        m.setAccessible(true);
        return (String) m.invoke(aspect, (Object) new Object[]{arg});
    }

    @Test
    void passwordFieldIsMaskedInDto() throws Exception {
        LoginDTO dto = new LoginDTO();
        dto.setUsername("admin");
        dto.setPassword("abc12345");

        String json = serialize(dto);

        assertTrue(json.contains("\"password\":\"***\""), "password 应被脱敏: " + json);
        assertFalse(json.contains("abc12345"), "原始密码不应出现: " + json);
        assertTrue(json.contains("\"username\":\"admin\""), "普通字段应保留: " + json);
    }

    @Test
    void allSensitiveFieldNamesAreMasked() throws Exception {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("oldPassword", "old-secret-1");
        map.put("newPassword", "new-secret-2");
        map.put("pwd", "pwd-secret-3");
        map.put("secret", "secret-4");
        map.put("token", "token-5");
        map.put("nickname", "张三");

        String json = serialize(map);

        for (String field : new String[]{"oldPassword", "newPassword", "pwd", "secret", "token"}) {
            assertTrue(json.contains("\"" + field + "\":\"***\""), field + " 应被脱敏: " + json);
        }
        for (String raw : new String[]{"old-secret-1", "new-secret-2", "pwd-secret-3", "secret-4", "token-5"}) {
            assertFalse(json.contains(raw), "原始敏感值不应出现: " + raw);
        }
        assertTrue(json.contains("张三"), "普通字段不受影响: " + json);
    }

    @Test
    void fieldNameMatchingIsCaseInsensitive() throws Exception {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("Password", "TopSecret1");
        map.put("TOKEN", "TopSecret2");

        String json = serialize(map);

        assertFalse(json.contains("TopSecret1"), "Password 应被脱敏: " + json);
        assertFalse(json.contains("TopSecret2"), "TOKEN 应被脱敏: " + json);
    }

    @Test
    void normalFieldsAreLeftUntouched() throws Exception {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("productName", "纯棉袜子");
        map.put("sortOrder", 42);

        String json = serialize(map);

        assertTrue(json.contains("纯棉袜子"));
        assertTrue(json.contains("42"));
        assertFalse(json.contains("***"), "无敏感字段时不应出现脱敏标记: " + json);
    }

    @Test
    void nullAndEmptyArgsReturnNull() throws Exception {
        Method m = AuditLogAspect.class.getDeclaredMethod("serializeArgs", Object[].class);
        m.setAccessible(true);
        assertNull(m.invoke(aspect, (Object) new Object[0]));
        assertNull(m.invoke(aspect, (Object) new Object[]{null, null}));
    }
}
