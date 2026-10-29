package com.trace.product.controller;

import com.trace.product.annotation.AuditLog;
import com.trace.product.annotation.RequirePerm;
import com.trace.product.common.Result;
import com.trace.product.entity.SysSiteConfig;
import com.trace.product.service.SiteConfigService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 站点/首页配置管理（后台）
 */
@RequirePerm("config")
@RestController
@RequestMapping("/admin/site-config")
@RequiredArgsConstructor
public class SiteConfigAdminController {

    private static final Set<String> ALLOWED_KEYS = Set.of(
            "site_name", "home_title", "home_subtitle", "home_banner", "home_recommend", "home_enabled"
    );

    private final SiteConfigService configService;

    @GetMapping
    public Result<List<SysSiteConfig>> list() {
        return Result.success(configService.all().entrySet().stream().map(e -> {
            SysSiteConfig c = new SysSiteConfig();
            c.setConfigKey(e.getKey());
            c.setConfigValue(e.getValue());
            return c;
        }).collect(Collectors.toList()));
    }

    /**
     * 批量保存站点配置
     */
    @AuditLog(module = "站点配置", action = "保存")
    @PostMapping("/save")
    public Result<Void> save(@RequestBody Map<String, String> values) {
        configService.saveAll(values, ALLOWED_KEYS);
        return Result.success();
    }
}
