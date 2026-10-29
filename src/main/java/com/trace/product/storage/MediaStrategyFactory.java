package com.trace.product.storage;

import com.trace.product.common.BusinessException;
import com.trace.product.service.StorageConfigService;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * 媒体存储策略工厂
 * <p>
 * 存储类型与参数来自数据库 sys_storage_config（后台可视化配置），
 * 每次调用都读取最新配置，因此切换存储后无需重启服务。
 * 业务层只依赖 StorageStrategy / VideoStrategy 接口，与具体存储解耦。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MediaStrategyFactory {

    private final List<StorageStrategy> storageStrategies;
    private final List<VideoStrategy> videoStrategies;
    private final StorageConfigService storageConfigService;

    private final Map<MediaStorageType, StorageStrategy> imageMap = new EnumMap<>(MediaStorageType.class);
    private final Map<MediaStorageType, VideoStrategy> videoMap = new EnumMap<>(MediaStorageType.class);

    @PostConstruct
    public void init() {
        storageStrategies.forEach(s -> imageMap.put(s.getType(), s));
        videoStrategies.forEach(s -> videoMap.put(s.getType(), s));
        log.info("图片存储策略: {}，视频存储策略: {}，当前配置: {}",
                imageMap.keySet(), videoMap.keySet(), storageConfigService.current().getType());
    }

    /** 当前生效的存储配置（数据库实时值） */
    public StorageSettings settings() {
        return storageConfigService.current();
    }

    /** 当前配置的存储类型 */
    public MediaStorageType currentType() {
        return MediaStorageType.of(settings().getType());
    }

    /** 获取当前激活的图片存储策略 */
    public StorageStrategy getStorageStrategy() {
        StorageStrategy strategy = imageMap.get(currentType());
        if (strategy == null) {
            throw new BusinessException("未找到图片存储策略: " + currentType());
        }
        return strategy;
    }

    /** 获取当前激活的视频存储策略 */
    public VideoStrategy getVideoStrategy() {
        VideoStrategy strategy = videoMap.get(currentType());
        if (strategy == null) {
            throw new BusinessException("未找到视频存储策略: " + currentType());
        }
        return strategy;
    }
}
