package com.trace.product.util;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

/**
 * IpUtils.getClientIp 纯静态方法单测（mock HttpServletRequest，离线可跑）
 */
@ExtendWith(MockitoExtension.class)
class IpUtilsTest {

    @Mock
    private HttpServletRequest request;

    @Test
    void nullRequestReturnsUnknown() {
        assertEquals("unknown", IpUtils.getClientIp(null));
    }

    @Test
    void xForwardedForMultiProxyTakesFirst() {
        when(request.getHeader("X-Forwarded-For")).thenReturn("1.2.3.4, 10.0.0.1, 172.16.0.5");
        assertEquals("1.2.3.4", IpUtils.getClientIp(request));
    }

    @Test
    void xRealIpUsedWhenPriorHeadersMissing() {
        when(request.getHeader("X-Forwarded-For")).thenReturn(null);
        when(request.getHeader("Proxy-Client-IP")).thenReturn(null);
        when(request.getHeader("WL-Proxy-Client-IP")).thenReturn(null);
        when(request.getHeader("X-Real-IP")).thenReturn("5.6.7.8");
        assertEquals("5.6.7.8", IpUtils.getClientIp(request));
    }

    @Test
    void blankXForwardedForFallsBackToRemoteAddr() {
        when(request.getHeader("X-Forwarded-For")).thenReturn("   ");
        when(request.getRemoteAddr()).thenReturn("9.9.9.9");
        assertEquals("9.9.9.9", IpUtils.getClientIp(request));
    }

    @Test
    void unknownHeaderValueFallsBackToRemoteAddr() {
        when(request.getHeader("X-Forwarded-For")).thenReturn("unknown");
        when(request.getRemoteAddr()).thenReturn("8.8.8.8");
        assertEquals("8.8.8.8", IpUtils.getClientIp(request));
    }

    @Test
    void emptyHeadersUseRemoteAddr() {
        when(request.getRemoteAddr()).thenReturn("203.0.113.10");
        assertEquals("203.0.113.10", IpUtils.getClientIp(request));
    }

    @Test
    void ipv6LoopbackNormalizedToIpv4() {
        when(request.getRemoteAddr()).thenReturn("0:0:0:0:0:0:0:1");
        assertEquals("127.0.0.1", IpUtils.getClientIp(request));
    }
}
