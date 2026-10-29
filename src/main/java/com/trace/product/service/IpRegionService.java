package com.trace.product.service;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.lionsoul.ip2region.xdb.Searcher;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;

/**
 * 离线 IP → 地理位置解析（ip2region xdb，内存加载，微秒级）
 * <p>
 * 数据文件默认取 classpath 下的 ip2region_v4.xdb；
 * 也可通过 trace.geo.xdb-path 指向外部文件，便于换库而不重新打包。
 * 解析失败或私有 IP 一律返回 null，绝不影响扫码主流程。
 */
@Slf4j
@Service
public class IpRegionService {

    private static final String DEFAULT_XDB = "ip2region_v4.xdb";

    /** 各省会/中心城市近似坐标，用于热力图落点（xdb 本身不含坐标） */
    private static final Map<String, double[]> PROVINCE_CENTER = Map.ofEntries(
            Map.entry("北京市", new double[]{39.9042, 116.4074}),
            Map.entry("天津市", new double[]{39.3434, 117.3616}),
            Map.entry("上海市", new double[]{31.2304, 121.4737}),
            Map.entry("重庆市", new double[]{29.5630, 106.5516}),
            Map.entry("河北省", new double[]{38.0428, 114.5149}),
            Map.entry("山西省", new double[]{37.8706, 112.5489}),
            Map.entry("辽宁省", new double[]{41.8057, 123.4315}),
            Map.entry("吉林省", new double[]{43.8171, 125.3235}),
            Map.entry("黑龙江省", new double[]{45.8038, 126.5350}),
            Map.entry("江苏省", new double[]{32.0603, 118.7969}),
            Map.entry("浙江省", new double[]{30.2741, 120.1551}),
            Map.entry("安徽省", new double[]{31.8206, 117.2272}),
            Map.entry("福建省", new double[]{26.0745, 119.2965}),
            Map.entry("江西省", new double[]{28.6820, 115.8579}),
            Map.entry("山东省", new double[]{36.6512, 117.1201}),
            Map.entry("河南省", new double[]{34.7466, 113.6254}),
            Map.entry("湖北省", new double[]{30.5928, 114.3055}),
            Map.entry("湖南省", new double[]{28.2282, 112.9388}),
            Map.entry("广东省", new double[]{23.1291, 113.2644}),
            Map.entry("海南省", new double[]{20.0440, 110.1999}),
            Map.entry("四川省", new double[]{30.5728, 104.0668}),
            Map.entry("贵州省", new double[]{26.6470, 106.6302}),
            Map.entry("云南省", new double[]{25.0389, 102.7183}),
            Map.entry("陕西省", new double[]{34.3416, 108.9398}),
            Map.entry("甘肃省", new double[]{36.0611, 103.8343}),
            Map.entry("青海省", new double[]{36.6171, 101.7782}),
            Map.entry("台湾省", new double[]{25.0330, 121.5654}),
            Map.entry("内蒙古自治区", new double[]{40.8414, 111.7519}),
            Map.entry("广西壮族自治区", new double[]{22.8170, 108.3665}),
            Map.entry("西藏自治区", new double[]{29.6520, 91.1721}),
            Map.entry("宁夏回族自治区", new double[]{38.4872, 106.2309}),
            Map.entry("新疆维吾尔自治区", new double[]{43.8256, 87.6168}),
            Map.entry("香港特别行政区", new double[]{22.3193, 114.1694}),
            Map.entry("澳门特别行政区", new double[]{22.1987, 113.5439})
    );

    @Value("${trace.geo.enabled:true}")
    private boolean enabled;

    @Value("${trace.geo.xdb-path:}")
    private String xdbPath;

    private Searcher searcher;

    @PostConstruct
    public void init() {
        if (!enabled) {
            log.info("扫码地理位置解析已关闭（trace.geo.enabled=false）");
            return;
        }
        try {
            byte[] buffer = loadXdb();
            searcher = Searcher.newWithBuffer(buffer);
            log.info("IP 地理位置库加载成功，来源={}，大小={} KB",
                    describeSource(), buffer.length / 1024);
        } catch (Exception e) {
            enabled = false;
            log.warn("IP 地理位置库加载失败，将跳过地理位置解析: {}", e.getMessage());
        }
    }

    private byte[] loadXdb() throws Exception {
        if (xdbPath != null && !xdbPath.isBlank()) {
            Path path = Paths.get(xdbPath);
            if (!Files.isReadable(path)) {
                throw new IllegalStateException("trace.geo.xdb-path 不可读: " + xdbPath);
            }
            return Files.readAllBytes(path);
        }
        try (InputStream in = new ClassPathResource(DEFAULT_XDB).getInputStream()) {
            return in.readAllBytes();
        }
    }

    private String describeSource() {
        return (xdbPath != null && !xdbPath.isBlank()) ? xdbPath : "classpath:" + DEFAULT_XDB;
    }

    /**
     * 解析 IP 归属地，失败返回 null
     */
    public GeoInfo resolve(String ip) {
        if (!enabled || searcher == null || ip == null || ip.isBlank() || isPrivateIp(ip)) {
            return null;
        }
        try {
            // v4 库格式：国家|省份|城市|运营商|国家代码，例如「中国|广东省|广州市|电信|CN」
            String region = searcher.search(ip);
            if (region == null || region.isBlank()) {
                return null;
            }
            String[] parts = region.split("\\|", -1);
            String country = normalize(parts, 0);
            String province = normalize(parts, 1);
            String city = normalize(parts, 2);
            double[] center = province == null ? null : PROVINCE_CENTER.get(province);
            BigDecimal lat = center == null ? null : BigDecimal.valueOf(center[0]);
            BigDecimal lng = center == null ? null : BigDecimal.valueOf(center[1]);
            return new GeoInfo(country, province, city, lat, lng);
        } catch (Exception e) {
            log.debug("IP 归属地解析失败: ip={}, err={}", ip, e.getMessage());
            return null;
        }
    }

    /** "0" 和空串表示该字段无数据 */
    private String normalize(String[] parts, int index) {
        if (index >= parts.length) {
            return null;
        }
        String value = parts[index].trim();
        if (value.isEmpty() || "0".equals(value) || "内网IP".equals(value)) {
            return null;
        }
        return value;
    }

    /** 私有/保留地址不查询 */
    private boolean isPrivateIp(String ip) {
        if (ip.startsWith("127.") || ip.startsWith("10.") || ip.startsWith("192.168.")
                || ip.startsWith("169.254.") || ip.startsWith("0.") || "::1".equals(ip)) {
            return true;
        }
        if (ip.startsWith("172.")) {
            String[] seg = ip.split("\\.");
            if (seg.length > 1) {
                try {
                    int second = Integer.parseInt(seg[1]);
                    return second >= 16 && second <= 31;
                } catch (NumberFormatException ignored) {
                    return false;
                }
            }
        }
        return false;
    }

    /** 解析结果 */
    public record GeoInfo(String country, String province, String city, BigDecimal lat, BigDecimal lng) {
    }
}
