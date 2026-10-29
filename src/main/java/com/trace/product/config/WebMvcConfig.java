package com.trace.product.config;

import com.trace.product.interceptor.AuthInterceptor;
import com.trace.product.interceptor.PermissionInterceptor;
import com.trace.product.interceptor.VerifyRateLimitInterceptor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Web MVC 配置：CORS 白名单 + 接口限流 + 后台登录鉴权
 * <p>
 * CORS 只允许配置里声明的域名，禁止使用通配符 *（否则任何站点都能带 Cookie 调后台接口）。
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class WebMvcConfig implements WebMvcConfigurer {

    private final VerifyRateLimitInterceptor verifyRateLimitInterceptor;
    private final AuthInterceptor authInterceptor;
    private final PermissionInterceptor permissionInterceptor;

    /** 允许的跨域来源，逗号分隔，见 trace.security.cors.allowed-origins */
    @Value("${trace.security.cors.allowed-origins:}")
    private String allowedOrigins;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(verifyRateLimitInterceptor)
                .addPathPatterns("/verify/**")
                .order(1);
        registry.addInterceptor(authInterceptor)
                .addPathPatterns("/admin/**")
                .excludePathPatterns("/admin/auth/login")
                .order(2);
        registry.addInterceptor(permissionInterceptor)
                .addPathPatterns("/admin/**")
                .excludePathPatterns("/admin/auth/login")
                .order(3);
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        String[] origins = parseOrigins();
        if (origins.length == 0) {
            // 未配置时不注册任何跨域规则：同域部署（Nginx 反代）本身就无需 CORS
            log.info("未配置 trace.security.cors.allowed-origins，跳过 CORS 注册（同域反代模式）");
            return;
        }
        log.info("CORS 白名单已启用: {}", String.join(", ", origins));
        registry.addMapping("/**")
                .allowedOrigins(origins)
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("Content-Type", "Authorization")
                .exposedHeaders("Content-Disposition")
                .allowCredentials(true)
                .maxAge(3600);
    }

    private String[] parseOrigins() {
        if (!StringUtils.hasText(allowedOrigins)) {
            return new String[0];
        }
        return java.util.Arrays.stream(allowedOrigins.split(","))
                .map(String::trim)
                .filter(StringUtils::hasText)
                .filter(o -> !"*".equals(o))
                .toArray(String[]::new);
    }
}
