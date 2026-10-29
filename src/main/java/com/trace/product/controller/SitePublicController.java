package com.trace.product.controller;

import cn.hutool.core.util.StrUtil;
import com.trace.product.common.Result;
import com.trace.product.entity.Product;
import com.trace.product.entity.SysSiteConfig;
import com.trace.product.mapper.ProductMapper;
import com.trace.product.mapper.SysSiteConfigMapper;
import com.trace.product.service.SiteConfigService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 用户端首页所需配置（公开接口，不走 admin 拦截）
 */
@RestController
@RequestMapping("/site")
@RequiredArgsConstructor
public class SitePublicController {

    private final SiteConfigService configService;
    private final ProductMapper productMapper;

    /**
     * 返回首页所需数据：站点标题、Banner、副标题、推荐产品、全量上架产品（推荐置顶）
     */
    @GetMapping("/home")
    public Result<Map<String, Object>> home() {
        Map<String, String> cfg = configService.all();
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("siteName", cfg.getOrDefault("site_name", "产品溯源"));
        out.put("homeTitle", cfg.getOrDefault("home_title", "产品溯源 · 品质保证"));
        out.put("homeSubtitle", cfg.getOrDefault("home_subtitle", ""));
        out.put("homeEnabled", "1".equals(cfg.getOrDefault("home_enabled", "1")));
        out.put("homeBanner", parseJsonArray(cfg.getOrDefault("home_banner", "")));

        // 推荐产品（如未配置则取最新上架的前 6 个）
        List<Long> ids = parseLongArray(cfg.getOrDefault("home_recommend", ""));
        List<Product> products;
        if (!ids.isEmpty()) {
            products = productMapper.selectBatchIds(ids);
        } else {
            products = productMapper.selectList(
                    new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<Product>()
                            .eq(Product::getShelfStatus, 1)
                            .orderByDesc(Product::getSortOrder).orderByDesc(Product::getId)
                            .last("LIMIT 6")
            );
        }
        out.put("recommend", products);
        // 全量上架产品（推荐置顶），首页列表数据源
        out.put("products", listProducts(null));
        return Result.success(out);
    }

    /**
     * 首页产品搜索：按产品名称 / 货号 / 编号模糊搜索，推荐产品置顶
     */
    @GetMapping("/products")
    public Result<Map<String, Object>> products(@org.springframework.web.bind.annotation.RequestParam(required = false) String keyword) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("keyword", keyword == null ? "" : keyword.trim());
        out.put("products", listProducts(keyword));
        return Result.success(out);
    }

    /**
     * 查询全部上架产品（上限 500 条保护），推荐产品按 home_recommend 配置顺序置顶，其余按 id DESC
     */
    private List<Product> listProducts(String keyword) {
        String kw = keyword == null ? "" : keyword.trim();
        com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<Product> qw =
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<Product>()
                        .eq(Product::getShelfStatus, 1)
                        .eq(Product::getDeleted, 0);
        if (StrUtil.isNotBlank(kw)) {
            String likeVal = "%" + kw + "%";
            // 名称 / 货号 / 编号 三字段模糊搜索（and 包裹保证与 shelf_status 条件是 AND 关系）
            qw.and(w -> w.like(Product::getProductName, likeVal)
                    .or().like(Product::getProductNo, likeVal)
                    .or().apply("CAST(id AS CHAR) LIKE {0}", likeVal));
        }
        List<Product> all = productMapper.selectList(qw.last("LIMIT 500"));
        // 推荐置顶：home_recommend 配置的 id 按配置顺序排最前
        List<Long> recommendIds = parseLongArray(configService.all().getOrDefault("home_recommend", ""));
        if (recommendIds.isEmpty()) {
            all.sort((a, b) -> Long.compare(b.getId(), a.getId()));
            return all;
        }
        Map<Long, Integer> order = new HashMap<>();
        for (int i = 0; i < recommendIds.size(); i++) order.put(recommendIds.get(i), i);
        all.sort((a, b) -> {
            Integer ia = order.get(a.getId());
            Integer ib = order.get(b.getId());
            if (ia != null && ib != null) return Integer.compare(ia, ib);
            if (ia != null) return -1;
            if (ib != null) return 1;
            return Long.compare(b.getId(), a.getId());
        });
        return all;
    }

    @GetMapping("/config")
    public Result<List<SysSiteConfig>> config() {
        return Result.success(configService.all().entrySet().stream().map(e -> {
            SysSiteConfig c = new SysSiteConfig();
            c.setConfigKey(e.getKey());
            c.setConfigValue(e.getValue());
            return c;
        }).collect(Collectors.toList()));
    }

    private List<String> parseJsonArray(String text) {
        if (StrUtil.isBlank(text)) return Collections.emptyList();
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper()
                    .readValue(text, new com.fasterxml.jackson.core.type.TypeReference<List<String>>() {});
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }

    private List<Long> parseLongArray(String text) {
        List<String> s = parseJsonArray(text);
        return s.stream().filter(StrUtil::isNotBlank).map(Long::valueOf).collect(Collectors.toList());
    }
}
