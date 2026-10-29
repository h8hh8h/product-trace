package com.trace.product.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 微信公众号配置（H5 微信 JS-SDK 分享）
 * <p>
 * 注意：公众号 AppID 与小程序 AppID 不同，需单独配置。
 */
@Data
@Component
@ConfigurationProperties(prefix = "wx.mp")
public class WxJsSdkProperties {

    private String appId = "";

    private String appSecret = "";

    public boolean configured() {
        return appId != null && !appId.isBlank() && appSecret != null && !appSecret.isBlank();
    }
}
