package com.trace.product.service;

import com.trace.product.entity.SysSiteConfig;
import com.trace.product.mapper.SysSiteConfigMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 站点/首页配置服务
 */
@Service
@RequiredArgsConstructor
public class SiteConfigService {

    private final SysSiteConfigMapper configMapper;

    /**
     * 读取全部配置，返回 key -> value 的 Map
     */
    public Map<String, String> all() {
        List<SysSiteConfig> list = configMapper.selectList(null);
        return list.stream().collect(Collectors.toMap(SysSiteConfig::getConfigKey, c -> c.getConfigValue() == null ? "" : c.getConfigValue(), (a, b) -> a, HashMap::new));
    }

    /**
     * 批量保存（存在则更新，不存在则插入）。仅保存允许的键。
     */
    public void saveAll(Map<String, String> values, Set<String> allowedKeys) {
        if (values == null || values.isEmpty()) return;
        LocalDateTime now = LocalDateTime.now();
        Map<String, SysSiteConfig> existing = configMapper.selectList(null).stream()
                .collect(Collectors.toMap(SysSiteConfig::getConfigKey, c -> c));
        for (Map.Entry<String, String> e : values.entrySet()) {
            String key = e.getKey();
            if (allowedKeys != null && !allowedKeys.contains(key)) continue;
            SysSiteConfig row = existing.get(key);
            if (row == null) {
                row = new SysSiteConfig();
                row.setConfigKey(key);
                row.setConfigValue(e.getValue() == null ? "" : e.getValue());
                row.setUpdateTime(now);
                configMapper.insert(row);
            } else {
                row.setConfigValue(e.getValue() == null ? "" : e.getValue());
                row.setUpdateTime(now);
                configMapper.updateById(row);
            }
        }
    }
}
