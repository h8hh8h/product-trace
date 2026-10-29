package com.trace.product.storage;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 媒体存储配置
 * <p>
 * 对应 application.yml：
 * <pre>
 * media:
 *   storage:
 *     type: local        # local / oss / vod
 *     local:
 *       root-path: /www/wwwroot/media
 *       url-prefix: https://your-domain.com/media
 * </pre>
 */
@Data
@Component
@ConfigurationProperties(prefix = "media")
public class MediaStorageProperties {

    private Storage storage = new Storage();

    @Data
    public static class Storage {
        /** 当前激活的存储类型: local / oss / vod */
        private String type = "local";
        private Local local = new Local();
        private Oss oss = new Oss();
        private Vod vod = new Vod();
    }

    @Data
    public static class Local {
        /** 本地存储根目录（宝塔环境）；实际生效值以数据库 sys_storage_config 为准 */
        private String rootPath = "/www/wwwroot/product-trace/upload";
        /** 对外访问前缀；留空返回相对路径，由 Nginx location /upload/ 直出 */
        private String urlPrefix = "/upload";
    }

    @Data
    public static class Oss {
        private String endpoint;
        private String accessKeyId;
        private String accessKeySecret;
        private String bucketName;
        private String urlPrefix;
    }

    @Data
    public static class Vod {
        private String accessKeyId;
        private String accessKeySecret;
        private String region;
        private String urlPrefix;
    }
}
