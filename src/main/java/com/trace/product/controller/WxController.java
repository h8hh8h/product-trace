package com.trace.product.controller;

import com.trace.product.common.Result;
import com.trace.product.service.WxJsSdkService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 微信相关接口
 */
@RestController
@RequestMapping("/wx")
@RequiredArgsConstructor
public class WxController {

    private final WxJsSdkService wxJsSdkService;

    /**
     * H5 微信 JS-SDK 签名
     */
    @GetMapping("/jssdk-config")
    public Result<Map<String, Object>> jssdkConfig(@RequestParam String url) {
        return Result.success(wxJsSdkService.signature(url));
    }
}
