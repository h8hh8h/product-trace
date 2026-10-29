package com.trace.product.storage.impl;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import com.trace.product.common.BusinessException;
import com.trace.product.service.StorageConfigService;
import com.trace.product.storage.MediaFileNames;
import com.trace.product.storage.MediaStorageType;
import com.trace.product.storage.MediaUploadResult;
import com.trace.product.storage.StorageSettings;
import com.trace.product.storage.StorageStrategy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * 本地图片存储策略（宝塔环境）
 * <p>
 * 图片保存到本地磁盘，上传时调用本地 cwebp 生成同名 .webp，
 * 由 Nginx 按 Accept 协商优先输出 WebP（见 deploy/nginx/bt_nginx_api.conf）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LocalStorageStrategy implements StorageStrategy {

    private static final DateTimeFormatter DIR_FMT = DateTimeFormatter.ofPattern("yyyy/MM/dd");
    private static final Set<String> IMAGE_EXT = Set.of("jpg", "jpeg", "png", "gif", "bmp", "webp", "svg");

    private final StorageConfigService storageConfigService;

    /** 当前生效的本地根目录（来自数据库配置，保存后立即生效） */
    private File rootDir() {
        StorageSettings settings = storageConfigService.current();
        return new File(settings.getLocalRootPath()).getAbsoluteFile();
    }

    @Override
    public MediaStorageType getType() {
        return MediaStorageType.LOCAL;
    }

    @Override
    public MediaUploadResult uploadImage(MultipartFile file, String dir) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException("上传图片不能为空");
        }
        String originalName = file.getOriginalFilename();
        String ext = FileUtil.extName(originalName);
        if (StrUtil.isBlank(ext) || !IMAGE_EXT.contains(ext.toLowerCase())) {
            throw new BusinessException("仅支持图片格式: " + IMAGE_EXT);
        }

        String dateDir = LocalDate.now().format(DIR_FMT);
        // 目录与 Nginx /uploads/images/ 对齐：media 根目录下的 images/yyyy/MM/dd
        String relativeDir = StrUtil.isBlank(dir) ? "images/" + dateDir : dir + "/images/" + dateDir;
        // 优先沿用前端语义化命名（{货号}_{时间戳}.jpg），不安全字符回退 UUID
        String fileName = MediaFileNames.resolve(originalName, ext.toLowerCase());

        // 转绝对路径：MultipartFile.transferTo 对相对路径会解析到 Tomcat 临时目录导致写入失败
        File destDir = new File(rootDir(), relativeDir);
        if (!destDir.exists() && !destDir.mkdirs()) {
            throw new BusinessException("创建存储目录失败: " + destDir.getAbsolutePath());
        }
        File destFile = new File(destDir, fileName);
        String safeName = MediaFileNames.avoidConflict(destFile, fileName, ext.toLowerCase());
        if (!safeName.equals(fileName)) {
            fileName = safeName;
            destFile = new File(destDir, fileName);
        }
        String storageKey = relativeDir + "/" + fileName;
        try {
            file.transferTo(destFile);
        } catch (IOException | IllegalStateException e) {
            log.error("图片保存失败: {}", destFile.getAbsolutePath(), e);
            throw new BusinessException("图片保存失败");
        }

        // JPG/PNG 在上传时同步生成 WebP（实时转码，失败不影响原图上传）
        String lowerExt = ext.toLowerCase();
        if ("jpg".equals(lowerExt) || "jpeg".equals(lowerExt) || "png".equals(lowerExt)) {
            generateWebp(destFile);
        }

        return MediaUploadResult.builder()
                .storageKey(storageKey)
                .fileUrl(getImageUrl(storageKey))
                .fileName(originalName)
                .fileSize(destFile.length())
                .mimeType(file.getContentType())
                .mediaType("image")
                .storageType(MediaStorageType.LOCAL.getCode())
                .build();
    }

    @Override
    public MediaUploadResult uploadImageBytes(byte[] data, String fileName, String dir) {
        if (data == null || data.length == 0) {
            throw new BusinessException("图片内容不能为空");
        }
        String ext = FileUtil.extName(fileName);
        if (StrUtil.isBlank(ext) || !IMAGE_EXT.contains(ext.toLowerCase())) {
            ext = "png";
        }

        String dateDir = LocalDate.now().format(DIR_FMT);
        String relativeDir = StrUtil.isBlank(dir) ? "images/" + dateDir : dir + "/images/" + dateDir;
        String storageKey = relativeDir + "/" + MediaFileNames.resolve(fileName, ext.toLowerCase());

        File destDir = new File(rootDir(), relativeDir);
        if (!destDir.exists() && !destDir.mkdirs()) {
            throw new BusinessException("创建存储目录失败: " + destDir.getAbsolutePath());
        }
        File destFile = new File(destDir, FileUtil.getName(storageKey));
        FileUtil.writeBytes(data, destFile);

        return MediaUploadResult.builder()
                .storageKey(storageKey)
                .fileUrl(getImageUrl(storageKey))
                .fileName(fileName)
                .fileSize(destFile.length())
                .mimeType("image/" + ext.toLowerCase())
                .mediaType("image")
                .storageType(MediaStorageType.LOCAL.getCode())
                .build();
    }

    @Override
    public String getImageUrl(String storageKey) {
        if (StrUtil.isBlank(storageKey)) {
            return null;
        }
        String prefix = storageConfigService.current().effectiveLocalPrefix();
        return prefix + "/" + storageKey;
    }

    @Override
    public boolean deleteImage(String storageKey) {
        if (StrUtil.isBlank(storageKey)) {
            return false;
        }
        File file = new File(rootDir(), storageKey);
        // 同时删除上传时生成的 .webp（命名规则：原文件名 + .webp）
        FileUtil.del(new File(file.getAbsolutePath() + ".webp"));
        return FileUtil.del(file);
    }

    /**
     * 调用本地 cwebp 生成 WebP，命名与原文件一致并追加 .webp，
     * 与 Nginx try_files $uri$webp_suffix 的规则对齐。
     * 未安装 cwebp 或转换失败时静默降级，仅保留原图。
     */
    private void generateWebp(File source) {
        File webp = new File(source.getAbsolutePath() + ".webp");
        try {
            Process process = new ProcessBuilder(
                    "cwebp", "-q", "80", source.getAbsolutePath(), "-o", webp.getAbsolutePath())
                    .redirectErrorStream(true)
                    .start();
            if (!process.waitFor(8, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                log.warn("WebP 转换超时，保留原图: {}", source.getName());
                FileUtil.del(webp);
                return;
            }
            if (process.exitValue() != 0) {
                log.warn("WebP 转换失败(exit={})，保留原图: {}", process.exitValue(), source.getName());
                FileUtil.del(webp);
            }
        } catch (Exception e) {
            log.debug("未安装 cwebp 或调用失败，跳过 WebP 转换: {}", e.getMessage());
            FileUtil.del(webp);
        }
    }
}
