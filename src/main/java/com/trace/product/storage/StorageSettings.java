package com.trace.product.storage;

import cn.hutool.core.util.StrUtil;
import lombok.Builder;
import lombok.Data;

/**
 * 运行时存储配置快照（每次上传/生成二维码时读取，来自数据库 sys_storage_config）
 */
@Data
@Builder
public class StorageSettings {

    /** local / oss */
    private String type;
    private String localRootPath;
    private String localUrlPrefix;
    private String ossAccessKeyId;
    private String ossAccessKeySecret;
    private String ossBucketName;
    private String ossEndpoint;
    private String ossUrlPrefix;

    public boolean isOss() {
        return "oss".equalsIgnoreCase(type);
    }

    /**
     * 本地存储的对外前缀：配置为空时统一返回 /upload（前端按同域解析）
     */
    public String effectiveLocalPrefix() {
        if (StrUtil.isBlank(localUrlPrefix)) {
            return "/upload";
        }
        String p = localUrlPrefix.trim();
        return p.endsWith("/") ? p.substring(0, p.length() - 1) : p;
    }

    /**
     * OSS 访问前缀：优先自定义域名，其次 https://{bucket}.{endpoint}
     */
    public String effectiveOssPrefix() {
        if (StrUtil.isNotBlank(ossUrlPrefix)) {
            String p = ossUrlPrefix.trim();
            return p.endsWith("/") ? p.substring(0, p.length() - 1) : p;
        }
        String ep = StrUtil.blankToDefault(ossEndpoint, "").trim();
        if (ep.startsWith("https://")) {
            ep = ep.substring("https://".length());
        } else if (ep.startsWith("http://")) {
            ep = ep.substring("http://".length());
        }
        if (ep.endsWith("/")) {
            ep = ep.substring(0, ep.length() - 1);
        }
        return "https://" + ossBucketName + "." + ep;
    }
}
