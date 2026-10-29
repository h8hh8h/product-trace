package com.trace.product.service;

import cn.hutool.core.util.RandomUtil;
import cn.hutool.crypto.digest.DigestUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.trace.product.common.BusinessException;
import com.trace.product.config.WxJsSdkProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 微信公众号 JS-SDK 签名服务
 * <p>
 * 用于 H5 在微信内自定义分享（updateAppMessageShareData / updateTimelineShareData）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WxJsSdkService {

    private static final String TOKEN_KEY = "wx:mp:access_token";
    private static final String TICKET_KEY = "wx:mp:jsapi_ticket";
    private static final String TOKEN_URL = "https://api.weixin.qq.com/cgi-bin/token";
    private static final String TICKET_URL = "https://api.weixin.qq.com/cgi-bin/ticket/getticket";

    private final WxJsSdkProperties properties;
    private final StringRedisTemplate stringRedisTemplate;
    private final RestClient restClient = RestClient.create();

    /**
     * 生成 wx.config 所需参数
     *
     * @param url 当前页面完整 URL（不含 # 及之后部分）
     */
    public Map<String, Object> signature(String url) {
        if (!properties.configured()) {
            throw new BusinessException("微信公众号 AppID/Secret 未配置，无法使用 JS-SDK 分享");
        }
        String ticket = getJsapiTicket();
        String nonceStr = RandomUtil.randomString(16);
        long timestamp = System.currentTimeMillis() / 1000L;

        String plain = "jsapi_ticket=" + ticket
                + "&noncestr=" + nonceStr
                + "&timestamp=" + timestamp
                + "&url=" + url;
        String signature = DigestUtil.sha1Hex(plain);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("appId", properties.getAppId());
        result.put("timestamp", timestamp);
        result.put("nonceStr", nonceStr);
        result.put("signature", signature);
        result.put("jsApiList", new String[]{
                "updateAppMessageShareData", "updateTimelineShareData"
        });
        return result;
    }

    private String getJsapiTicket() {
        String cached = stringRedisTemplate.opsForValue().get(TICKET_KEY);
        if (cached != null && !cached.isBlank()) {
            return cached;
        }
        String accessToken = getAccessToken();
        String url = UriComponentsBuilder.fromHttpUrl(TICKET_URL)
                .queryParam("access_token", accessToken)
                .queryParam("type", "jsapi")
                .toUriString();

        String body = restClient.get().uri(url).retrieve().body(String.class);
        JSONObject json = JSONUtil.parseObj(body);
        String ticket = json.getStr("ticket");
        int errcode = json.getInt("errcode", 0);
        if (errcode != 0 || ticket == null || ticket.isBlank()) {
            throw new BusinessException("获取微信 jsapi_ticket 失败: errcode="
                    + errcode + ", errmsg=" + json.getStr("errmsg"));
        }
        stringRedisTemplate.opsForValue().set(TICKET_KEY, ticket, Duration.ofSeconds(7000));
        return ticket;
    }

    private String getAccessToken() {
        String cached = stringRedisTemplate.opsForValue().get(TOKEN_KEY);
        if (cached != null && !cached.isBlank()) {
            return cached;
        }
        String url = UriComponentsBuilder.fromHttpUrl(TOKEN_URL)
                .queryParam("grant_type", "client_credential")
                .queryParam("appid", properties.getAppId())
                .queryParam("secret", properties.getAppSecret())
                .toUriString();

        String body = restClient.get().uri(url).retrieve().body(String.class);
        JSONObject json = JSONUtil.parseObj(body);
        String accessToken = json.getStr("access_token");
        if (accessToken == null || accessToken.isBlank()) {
            throw new BusinessException("获取公众号 access_token 失败: errcode="
                    + json.getInt("errcode") + ", errmsg=" + json.getStr("errmsg"));
        }
        stringRedisTemplate.opsForValue().set(TOKEN_KEY, accessToken, Duration.ofSeconds(7000));
        return accessToken;
    }
}
