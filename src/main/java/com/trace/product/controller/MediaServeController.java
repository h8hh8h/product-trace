package com.trace.product.controller;

import cn.hutool.core.io.FileUtil;
import com.trace.product.service.StorageConfigService;
import com.trace.product.storage.MediaStorageType;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * 本地媒体文件直出控制器
 * <p>
 * 替代 Nginx 的硬编码 alias：Nginx 将 /upload/** 反代到后端 8080，
 * 后端从 sys_storage_config.local_root_path 读取文件并流式返回。
 * 这样存储路径改任何位置都能正确访问，无需同步改 Nginx。
 * <p>
 * 仅当存储类型为 local 时生效；OSS 存储由浏览器直接请求 OSS 域名，不经过这里。
 * CSV 目录保持禁止公网直链访问（deny all）。
 */
@Slf4j
@RestController
@RequiredArgsConstructor
public class MediaServeController {

    private final StorageConfigService storageConfigService;

    /** 禁止公网直链访问的子目录（只能走后台接口下载） */
    private static final String[] FORBIDDEN_PREFIXES = {"csv"};

    /** MIME 类型映射（部分格式 Spring 默认识别不全） */
    private static MediaType guessContentType(Path path) {
        try {
            String mime = Files.probeContentType(path);
            if (mime != null) {
                return MediaType.parseMediaType(mime);
            }
        } catch (IOException ignored) {
        }
        String name = path.getFileName().toString().toLowerCase();
        if (name.endsWith(".webp")) return MediaType.parseMediaType("image/webp");
        if (name.endsWith(".mp4")) return MediaType.parseMediaType("video/mp4");
        if (name.endsWith(".webm")) return MediaType.parseMediaType("video/webm");
        if (name.endsWith(".svg")) return MediaType.parseMediaType("image/svg+xml");
        return MediaType.APPLICATION_OCTET_STREAM;
    }

    @GetMapping(value = "/upload/**")
    public ResponseEntity<Resource> serve(HttpServletRequest request, HttpServletResponse response) {
        // 1. 仅本地存储时由后端直出；OSS 由浏览器直接请求 OSS 域名
        var settings = storageConfigService.current();
        if (MediaStorageType.OSS.getCode().equalsIgnoreCase(settings.getType())) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }

        String rootPath = settings.getLocalRootPath();
        if (rootPath == null || rootPath.isBlank()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }

        // 2. 解析相对路径
        String uri = request.getRequestURI();
        // requestURI 是 /upload/images/xxx.jpg → 去掉 /upload/ 前缀
        String relative = uri;
        int idx = relative.indexOf("/upload/");
        if (idx >= 0) {
            relative = relative.substring(idx + "/upload/".length());
        }
        // 去掉可能的查询串
        int q = relative.indexOf('?');
        if (q >= 0) {
            relative = relative.substring(0, q);
        }

        // 3. 安全检查：路径穿越
        if (relative.contains("..")) {
            log.warn("疑似路径穿越请求: {}", uri);
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        // 4. CSV 目录禁止公网直链
        String lower = relative.toLowerCase();
        for (String prefix : FORBIDDEN_PREFIXES) {
            if (lower.startsWith(prefix + "/") || lower.equals(prefix)) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
            }
        }

        // 5. 定位文件
        File rootDir = new File(rootPath).getAbsoluteFile();
        File file = new File(rootDir, relative);
        // 再做一次绝对路径校验，确保在 rootDir 内
        File absFile = file.getAbsoluteFile();
        if (!absFile.getPath().startsWith(rootDir.getPath())) {
            log.warn("越权访问: {} -> {}", uri, absFile);
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        if (!absFile.exists() || !absFile.isFile()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }

        // 5.1 WebP 内容协商：浏览器 Accept 包含 image/webp 时，优先返回上传时生成的 .webp 变体
        File serveFile = absFile;
        String accept = request.getHeader(HttpHeaders.ACCEPT);
        if (accept != null && accept.contains("image/webp")) {
            String nameLower = absFile.getName().toLowerCase();
            boolean lossyImg = nameLower.endsWith(".jpg") || nameLower.endsWith(".jpeg") || nameLower.endsWith(".png");
            if (lossyImg) {
                File webpVariant = new File(absFile.getAbsolutePath() + ".webp");
                if (webpVariant.exists() && webpVariant.isFile()) {
                    serveFile = webpVariant;
                }
            }
        }

        try {
            Path path = serveFile.toPath();
            MediaType contentType = guessContentType(path);
            long contentLength = serveFile.length();

            // 6. 构建响应，支持 Range（视频拖动）
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(contentType);
            headers.setContentLength(contentLength);
            headers.setCacheControl("public, max-age=2592000");
            headers.set("Access-Control-Allow-Origin", "*");
            // 文件名 URL 编码（兼容 Content-Disposition inline 场景）
            String fileName = URLEncoder.encode(serveFile.getName(), StandardCharsets.UTF_8).replace("+", "%20");
            headers.set(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + fileName + "\"");

            Resource resource = new FileSystemResource(serveFile);
            return new ResponseEntity<>(resource, headers, HttpStatus.OK);
        } catch (Exception e) {
            log.error("读取媒体文件失败: {}", serveFile.getAbsolutePath(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
}
