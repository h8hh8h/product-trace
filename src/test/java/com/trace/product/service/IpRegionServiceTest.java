package com.trace.product.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.*;

/**
 * IpRegionService 私有 IP 判定单测。
 * 直接 new 对象 + 反射调用 private isPrivateIp，不触发 @PostConstruct（避免加载 xdb）。
 */
class IpRegionServiceTest {

    private final IpRegionService service = new IpRegionService();

    private boolean invokeIsPrivateIp(String ip) throws Exception {
        Method m = IpRegionService.class.getDeclaredMethod("isPrivateIp", String.class);
        m.setAccessible(true);
        return (boolean) m.invoke(service, ip);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "127.0.0.1", "127.255.255.254",
            "10.0.0.1", "10.255.255.255",
            "192.168.0.1", "192.168.31.100",
            "172.16.0.1", "172.20.3.4", "172.31.255.255",
            "169.254.1.1",
            "0.0.0.0",
            "::1"
    })
    void privateAndReservedIpsAreRecognized(String ip) throws Exception {
        assertTrue(invokeIsPrivateIp(ip), ip + " 应判定为私有/保留地址");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "8.8.8.8", "1.2.3.4", "203.0.113.5",
            "172.15.0.1",   // 172.16-31 才是私有段
            "172.32.0.1",
            "11.0.0.1",     // 仅 10.x 是私有段
            "193.168.1.1"
    })
    void publicIpsAreNotPrivate(String ip) throws Exception {
        assertFalse(invokeIsPrivateIp(ip), ip + " 不应判定为私有地址");
    }

    @Test
    void resolveReturnsNullForPrivateIp() {
        // 未初始化 xdb（enabled 默认 false），私有 IP 一律返回 null，绝不抛异常
        assertNull(service.resolve("127.0.0.1"));
        assertNull(service.resolve("192.168.1.100"));
        assertNull(service.resolve(null));
        assertNull(service.resolve(""));
    }

    @Test
    void malformed172SegmentNotTreatedAsPrivate() throws Exception {
        // 172.x 第二段非数字时走 NumberFormatException 分支，返回 false
        assertFalse(invokeIsPrivateIp("172.abc.0.1"));
    }
}
