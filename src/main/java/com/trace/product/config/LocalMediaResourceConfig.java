package com.trace.product.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.io.File;

/**
 * 本地开发环境静态资源映射
 * <p>
 * 生产环境由 Nginx 直接提供 /media 静态访问，无需该配置。
 * 本地启动后可通过 http://127.0.0.1:8080/api/media/xxx 预览已上传文件。
 */
@Configuration
@Profile("dev")
public class LocalMediaResourceConfig implements WebMvcConfigurer {

    @Value("${media.storage.local.root-path:./upload}")
    private String rootPath;

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        String location = new File(rootPath).toURI().toString();
        registry.addResourceHandler("/media/**").addResourceLocations(location);
    }
}
