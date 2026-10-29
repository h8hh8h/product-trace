package com.trace.product.service;

import cn.hutool.core.util.StrUtil;
import com.trace.product.common.BusinessException;
import com.trace.product.entity.SysStorageConfig;
import com.trace.product.mapper.SysStorageConfigMapper;
import com.trace.product.storage.MediaStorageProperties;
import com.trace.product.storage.StorageSettings;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;

import java.io.File;
import java.time.LocalDateTime;
import java.util.Set;

/**
 * 存储配置服务
 * <p>
 * 配置存放在 sys_storage_config（单行，id=1），启动后首次使用时加载并缓存在内存，
 * 后台保存配置后立即刷新缓存——上传图片/视频、生成二维码时读取的都是最新配置，
 * 不需要重启服务，也不依赖 yml 的启动期注入。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StorageConfigService {

    private static final long CONFIG_ID = 1L;
    private static final Set<String> ALLOWED_TYPES = Set.of("local", "oss");

    private final SysStorageConfigMapper mapper;
    private final MediaStorageProperties ymlDefaults;

    private volatile StorageSettings cached;

    /**
     * 当前生效的存储配置。表不存在/读取失败时回退到 yml 配置，保证服务可用。
     */
    public StorageSettings current() {
        StorageSettings local = cached;
        if (local != null) {
            return local;
        }
        synchronized (this) {
            if (cached == null) {
                cached = loadFromDb();
            }
            return cached;
        }
    }

    /** 后台读取配置（密码字段做脱敏，仅返回是否已配置） */
    public SysStorageConfig loadForAdmin() {
        SysStorageConfig row = readRow();
        if (row == null) {
            row = new SysStorageConfig();
            row.setId(CONFIG_ID);
            row.setStorageType(ymlDefaults.getStorage().getType());
            row.setLocalRootPath(ymlDefaults.getStorage().getLocal().getRootPath());
            row.setLocalUrlPrefix(ymlDefaults.getStorage().getLocal().getUrlPrefix());
        }
        // AccessKeySecret 不回传明文
        row.setOssAccessKeySecret(StrUtil.isBlank(row.getOssAccessKeySecret()) ? "" : "******");
        return row;
    }

    /**
     * 保存配置。secret 传 "******" 表示保持原值不变。
     */
    public void save(SysStorageConfig dto) {
        if (dto == null) {
            throw new BusinessException("配置不能为空");
        }
        String type = StrUtil.blankToDefault(dto.getStorageType(), "local").trim().toLowerCase();
        if (!ALLOWED_TYPES.contains(type)) {
            throw new BusinessException("存储类型仅支持 local / oss");
        }
        dto.setStorageType(type);

        if ("local".equals(type)) {
            if (StrUtil.isBlank(dto.getLocalRootPath())) {
                throw new BusinessException("本地存储根目录不能为空");
            }
            String path = dto.getLocalRootPath().trim();
            if (!path.startsWith("/")) {
                throw new BusinessException("本地存储根目录必须是以 / 开头的绝对路径（例：/www/wwwroot/product-trace/upload）");
            }
            File dir = new File(path);
            if (!dir.exists() && !dir.mkdirs()) {
                throw new BusinessException("本地存储根目录不存在且创建失败：" + path + "（请先在宝塔中创建并授权 www 用户写入）");
            }
            if (!dir.canWrite()) {
                throw new BusinessException("本地存储根目录没有写入权限：" + path + "（执行 chown -R www:www " + path + "）");
            }
            dto.setLocalRootPath(path);
        } else {
            if (StrUtil.isBlank(dto.getOssAccessKeyId()) || StrUtil.isBlank(dto.getOssAccessKeySecret())
                    || StrUtil.isBlank(dto.getOssBucketName()) || StrUtil.isBlank(dto.getOssEndpoint())) {
                throw new BusinessException("OSS 配置不完整：AccessKey ID / Secret / Bucket / Endpoint 均不能为空");
            }
        }

        SysStorageConfig existing = readRow();
        dto.setId(CONFIG_ID);
        dto.setUpdateTime(LocalDateTime.now());
        if (existing != null) {
            // secret 未修改时沿用旧值
            if ("******".equals(dto.getOssAccessKeySecret())) {
                dto.setOssAccessKeySecret(existing.getOssAccessKeySecret());
            }
            mapper.updateById(dto);
            if (dto.getId() == null) {
                dto.setId(CONFIG_ID);
            }
        } else {
            mapper.insert(dto);
        }
        reload();
        log.info("存储配置已更新：type={}, localRoot={}, ossBucket={}",
                dto.getStorageType(), dto.getLocalRootPath(), dto.getOssBucketName());
    }

    /** 强制重新加载 */
    public void reload() {
        synchronized (this) {
            cached = loadFromDb();
        }
    }

    private SysStorageConfig readRow() {
        try {
            return mapper.selectById(CONFIG_ID);
        } catch (DataAccessException e) {
            log.error("读取 sys_storage_config 失败（将回退到 yml 配置）：{}", e.getMostSpecificCause().getMessage());
            return null;
        }
    }

    private StorageSettings loadFromDb() {
        SysStorageConfig row = readRow();
        if (row == null) {
            MediaStorageProperties.Storage s = ymlDefaults.getStorage();
            log.warn("未找到存储配置记录，使用 application.yml 默认值：type={}, root={}",
                    s.getType(), s.getLocal().getRootPath());
            return StorageSettings.builder()
                    .type(StrUtil.blankToDefault(s.getType(), "local"))
                    .localRootPath(s.getLocal().getRootPath())
                    .localUrlPrefix(s.getLocal().getUrlPrefix())
                    .ossAccessKeyId(s.getOss().getAccessKeyId())
                    .ossAccessKeySecret(s.getOss().getAccessKeySecret())
                    .ossBucketName(s.getOss().getBucketName())
                    .ossEndpoint(s.getOss().getEndpoint())
                    .ossUrlPrefix(s.getOss().getUrlPrefix())
                    .build();
        }
        return StorageSettings.builder()
                .type(StrUtil.blankToDefault(row.getStorageType(), "local"))
                .localRootPath(StrUtil.blankToDefault(row.getLocalRootPath(),
                        ymlDefaults.getStorage().getLocal().getRootPath()))
                .localUrlPrefix(row.getLocalUrlPrefix())
                .ossAccessKeyId(row.getOssAccessKeyId())
                .ossAccessKeySecret(row.getOssAccessKeySecret())
                .ossBucketName(row.getOssBucketName())
                .ossEndpoint(row.getOssEndpoint())
                .ossUrlPrefix(row.getOssUrlPrefix())
                .build();
    }
}
