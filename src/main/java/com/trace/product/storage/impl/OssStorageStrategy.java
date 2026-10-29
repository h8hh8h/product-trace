package com.trace.product.storage.impl;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.crypto.digest.DigestUtil;
import com.trace.product.common.BusinessException;
import com.trace.product.service.StorageConfigService;
import com.trace.product.storage.MediaFileNames;
import com.trace.product.storage.MediaStorageType;
import com.trace.product.storage.MediaUploadResult;
import com.trace.product.storage.StorageSettings;
import com.trace.product.storage.StorageStrategy;
import com.trace.product.storage.VideoStrategy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 阿里云 OSS 存储策略
 * <p>
 * 直接使用 OSS 的 REST 接口（PUT Object / DELETE Object，签名 V1），
 * 不引入额外 SDK，避免与 Spring Boot 的 HttpClient 版本冲突。
 * AccessKey / Bucket / Endpoint 全部来自后台「存储配置」页面，保存后即时生效。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OssStorageStrategy implements StorageStrategy, VideoStrategy {

    private static final DateTimeFormatter DIR_FMT = DateTimeFormatter.ofPattern("yyyy/MM/dd");
    private static final DateTimeFormatter HTTP_DATE =
            DateTimeFormatter.RFC_1123_DATE_TIME.withZone(ZoneOffset.UTC).withLocale(Locale.US);

    private static final Set<String> IMAGE_EXT = Set.of("jpg", "jpeg", "png", "gif", "bmp", "webp");
    private static final Set<String> VIDEO_EXT = Set.of("mp4", "webm", "mov");

    private static final Map<String, String> MIME = Map.ofEntries(
            Map.entry("jpg", "image/jpeg"),
            Map.entry("jpeg", "image/jpeg"),
            Map.entry("png", "image/png"),
            Map.entry("gif", "image/gif"),
            Map.entry("bmp", "image/bmp"),
            Map.entry("webp", "image/webp"),
            Map.entry("mp4", "video/mp4"),
            Map.entry("webm", "video/webm"),
            Map.entry("mov", "video/quicktime")
    );

    private final StorageConfigService storageConfigService;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    @Override
    public MediaStorageType getType() {
        return MediaStorageType.OSS;
    }

    // ================= 图片 =================

    @Override
    public MediaUploadResult uploadImage(MultipartFile file, String dir) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException("上传图片不能为空");
        }
        String ext = FileUtil.extName(file.getOriginalFilename());
        if (StrUtil.isBlank(ext) || !IMAGE_EXT.contains(ext.toLowerCase(Locale.ROOT))) {
            throw new BusinessException("仅支持图片格式: " + IMAGE_EXT);
        }
        try {
            return uploadBytes(file.getBytes(), ext, file.getOriginalFilename(),
                    file.getContentType(), "image", "images", dir);
        } catch (IOException e) {
            throw new BusinessException("读取上传图片失败");
        }
    }

    @Override
    public MediaUploadResult uploadImageBytes(byte[] data, String fileName, String dir) {
        String ext = FileUtil.extName(fileName);
        if (StrUtil.isBlank(ext) || !IMAGE_EXT.contains(ext.toLowerCase(Locale.ROOT))) {
            ext = "png";
        }
        return uploadBytes(data, ext, fileName, "image/" + ext.toLowerCase(Locale.ROOT), "image", "images", dir);
    }

    @Override
    public String getImageUrl(String storageKey) {
        return joinUrl(storageConfigService.current(), storageKey);
    }

    @Override
    public boolean deleteImage(String storageKey) {
        return deleteObject(storageKey);
    }

    // ================= 视频 =================

    @Override
    public MediaUploadResult uploadVideo(MultipartFile file, String dir) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException("上传视频不能为空");
        }
        String ext = FileUtil.extName(file.getOriginalFilename());
        if (StrUtil.isBlank(ext) || !VIDEO_EXT.contains(ext.toLowerCase(Locale.ROOT))) {
            throw new BusinessException("仅支持视频格式: " + VIDEO_EXT);
        }
        try {
            return uploadBytes(file.getBytes(), ext, file.getOriginalFilename(),
                    file.getContentType(), "video", "videos", dir);
        } catch (IOException e) {
            throw new BusinessException("读取上传视频失败");
        }
    }

    @Override
    public String getVideoUrl(String storageKey) {
        return joinUrl(storageConfigService.current(), storageKey);
    }

    @Override
    public boolean deleteVideo(String storageKey) {
        return deleteObject(storageKey);
    }

    // ================= 内部实现 =================

    private MediaUploadResult uploadBytes(byte[] data, String ext, String originalName, String mime,
                                          String mediaType, String kind, String dir) {
        StorageSettings settings = storageConfigService.current();
        if (!settings.isOss()) {
            throw new BusinessException("当前存储类型不是 OSS，请先在「存储配置」中切换为阿里云 OSS");
        }
        validateSettings(settings);

        String dateDir = LocalDate.now().format(DIR_FMT);
        String relativeDir = StrUtil.isBlank(dir) ? kind + "/" + dateDir : dir + "/" + kind + "/" + dateDir;
        String storageKey = relativeDir + "/" + MediaFileNames.resolve(originalName, ext.toLowerCase(Locale.ROOT));
        String contentType = StrUtil.blankToDefault(mime, MIME.getOrDefault(ext.toLowerCase(Locale.ROOT), "application/octet-stream"));

        putObject(settings, storageKey, data, contentType);

        return MediaUploadResult.builder()
                .storageKey(storageKey)
                .fileUrl(joinUrl(settings, storageKey))
                .fileName(originalName)
                .fileSize((long) data.length)
                .mimeType(contentType)
                .mediaType(mediaType)
                .storageType(MediaStorageType.OSS.getCode())
                .build();
    }

    /** 连通性自检：上传并删除一个 4 字节测试对象 */
    public void testConnection() {
        StorageSettings settings = storageConfigService.current();
        if (!settings.isOss()) {
            throw new BusinessException("当前存储类型为本地存储，无需测试 OSS");
        }
        validateSettings(settings);
        String key = "test/healthcheck-" + IdUtil.fastSimpleUUID() + ".txt";
        byte[] data = "ping".getBytes(StandardCharsets.UTF_8);
        putObject(settings, key, data, "text/plain");
        deleteObject(key);
    }

    private void validateSettings(StorageSettings s) {
        if (StrUtil.hasBlank(s.getOssAccessKeyId(), s.getOssAccessKeySecret(),
                s.getOssBucketName(), s.getOssEndpoint())) {
            throw new BusinessException("OSS 配置不完整，请先在「存储配置」中填写 AccessKey / Bucket / Endpoint");
        }
    }

    private void putObject(StorageSettings s, String key, byte[] data, String contentType) {
        String url = objectUrl(s, key);
        String date = HTTP_DATE.format(ZonedDateTime.now(ZoneOffset.UTC));
        String contentMd5 = Base64.getEncoder().encodeToString(DigestUtil.md5(data));
        String authorization = sign("PUT", s, key, contentMd5, contentType, date);

        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(60))
                .header("Date", date)
                .header("Content-Type", contentType)
                .header("Content-MD5", contentMd5)
                .header("Authorization", authorization)
                .PUT(HttpRequest.BodyPublishers.ofByteArray(data))
                .build();
        try {
            HttpResponse<String> resp = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() / 100 != 2) {
                log.error("OSS 上传失败 status={} key={} resp={}", resp.statusCode(), key, resp.body());
                throw new BusinessException("OSS 上传失败（HTTP " + resp.statusCode() + "）：" + brief(resp.body()));
            }
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            log.error("OSS 上传异常 key={}", key, e);
            throw new BusinessException("OSS 上传异常：" + e.getMessage());
        }
    }

    private boolean deleteObject(String storageKey) {
        if (StrUtil.isBlank(storageKey)) {
            return false;
        }
        StorageSettings s = storageConfigService.current();
        if (!s.isOss()) {
            return false;
        }
        try {
            String url = objectUrl(s, storageKey);
            String date = HTTP_DATE.format(ZonedDateTime.now(ZoneOffset.UTC));
            String authorization = sign("DELETE", s, storageKey, "", "", date);
            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(30))
                    .header("Date", date)
                    .header("Authorization", authorization)
                    .DELETE()
                    .build();
            HttpResponse<String> resp = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            boolean ok = resp.statusCode() / 100 == 2 || resp.statusCode() == 404;
            if (!ok) {
                log.warn("OSS 删除失败 status={} key={} resp={}", resp.statusCode(), storageKey, resp.body());
            }
            return ok;
        } catch (Exception e) {
            log.warn("OSS 删除异常 key={}: {}", storageKey, e.getMessage());
            return false;
        }
    }

    /** OSS V1 签名：OSS AccessKeyId:Base64(HmacSHA1(secret, StringToSign)) */
    private String sign(String method, StorageSettings s, String key, String contentMd5,
                        String contentType, String date) {
        String resource = "/" + s.getOssBucketName() + "/" + key;
        String stringToSign = method + "\n" + contentMd5 + "\n" + contentType + "\n" + date + "\n" + resource;
        try {
            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(s.getOssAccessKeySecret().getBytes(StandardCharsets.UTF_8), "HmacSHA1"));
            byte[] digest = mac.doFinal(stringToSign.getBytes(StandardCharsets.UTF_8));
            return "OSS " + s.getOssAccessKeyId() + ":" + Base64.getEncoder().encodeToString(digest);
        } catch (Exception e) {
            throw new BusinessException("OSS 签名失败：" + e.getMessage());
        }
    }

    private String objectUrl(StorageSettings s, String key) {
        String host = hostOf(s);
        String encodedKey = encodeKey(key);
        return "https://" + host + "/" + encodedKey;
    }

    private String hostOf(StorageSettings s) {
        String ep = s.getOssEndpoint().trim();
        if (ep.startsWith("https://")) {
            ep = ep.substring("https://".length());
        } else if (ep.startsWith("http://")) {
            ep = ep.substring("http://".length());
        }
        if (ep.endsWith("/")) {
            ep = ep.substring(0, ep.length() - 1);
        }
        return s.getOssBucketName() + "." + ep;
    }

    private String encodeKey(String key) {
        StringBuilder sb = new StringBuilder();
        for (String seg : key.split("/")) {
            if (sb.length() > 0) {
                sb.append('/');
            }
            sb.append(URLEncoder.encode(seg, StandardCharsets.UTF_8).replace("+", "%20"));
        }
        return sb.toString();
    }

    private String joinUrl(StorageSettings s, String storageKey) {
        if (StrUtil.isBlank(storageKey)) {
            return null;
        }
        return s.effectiveOssPrefix() + "/" + storageKey;
    }

    private String brief(String body) {
        if (body == null) {
            return "";
        }
        String text = body.replaceAll("<[^>]+>", " ").replaceAll("\\s+", " ").trim();
        return text.length() > 200 ? text.substring(0, 200) : text;
    }
}
