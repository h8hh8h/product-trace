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
import com.trace.product.storage.VideoStrategy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Set;

/**
 * 本地视频存储策略（宝塔环境）
 * <p>
 * 使用 FFmpeg 将上传视频统一转码为 H.264 / MP4（faststart 便于边下边播）。
 * 若服务器未安装 FFmpeg，则保留原文件并记录警告。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LocalVideoStrategy implements VideoStrategy {

    private static final DateTimeFormatter DIR_FMT = DateTimeFormatter.ofPattern("yyyy/MM/dd");
    private static final Set<String> VIDEO_EXT = Set.of("mp4", "avi", "mov", "mkv", "flv", "wmv", "webm");

    private final StorageConfigService storageConfigService;

    private File rootDir() {
        StorageSettings settings = storageConfigService.current();
        return new File(settings.getLocalRootPath()).getAbsoluteFile();
    }

    @Override
    public MediaStorageType getType() {
        return MediaStorageType.LOCAL;
    }

    @Override
    public MediaUploadResult uploadVideo(MultipartFile file, String dir) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException("上传视频不能为空");
        }
        String originalName = file.getOriginalFilename();
        String ext = FileUtil.extName(originalName);
        if (StrUtil.isBlank(ext) || !VIDEO_EXT.contains(ext.toLowerCase())) {
            throw new BusinessException("仅支持视频格式: " + VIDEO_EXT);
        }

        String dateDir = LocalDate.now().format(DIR_FMT);
        // 目录与 Nginx /uploads/videos/ 对齐：media 根目录下的 videos/yyyy/MM/dd
        String relativeDir = StrUtil.isBlank(dir) ? "videos/" + dateDir : dir + "/videos/" + dateDir;
        // 优先沿用前端语义化命名（{货号}_{时间戳}.mp4），不安全字符回退 UUID
        String fileName = MediaFileNames.resolve(originalName, ext.toLowerCase());
        String storageKey = relativeDir + "/" + fileName;

        // 转绝对路径：MultipartFile.transferTo 对相对路径会解析到 Tomcat 临时目录导致写入失败
        File destDir = new File(rootDir(), relativeDir).getAbsoluteFile();
        if (!destDir.exists() && !destDir.mkdirs()) {
            throw new BusinessException("创建存储目录失败: " + destDir.getAbsolutePath());
        }
        File destFile = new File(destDir, fileName);
        String safeName = MediaFileNames.avoidConflict(destFile, fileName, ext.toLowerCase());
        if (!safeName.equals(fileName)) {
            fileName = safeName;
            destFile = new File(destDir, fileName);
            storageKey = relativeDir + "/" + fileName;
        }
        try {
            file.transferTo(destFile);
        } catch (IOException | IllegalStateException e) {
            log.error("视频保存失败: {}", destFile.getAbsolutePath(), e);
            throw new BusinessException("视频保存失败");
        }

        String finalKey = storageKey;
        File finalFile = destFile;
        Integer duration = null;
        // 原文件已是 mp4 则跳过转码，避免 ffmpeg -y 同名覆写导致源文件被清空
        String lowerExt = ext.toLowerCase();
        if (!"mp4".equals(lowerExt)) {
            try {
                File mp4File = transcode(destFile);
                if (mp4File != null) {
                    finalFile = mp4File;
                    finalKey = relativeDir + "/" + mp4File.getName();
                    duration = probeDuration(mp4File.getAbsolutePath());
                }
            } catch (Exception e) {
                log.warn("视频转码失败，保留原文件: {}", e.getMessage());
            }
        }

        return MediaUploadResult.builder()
                .storageKey(finalKey)
                .fileUrl(getVideoUrl(finalKey))
                .fileName(originalName)
                .fileSize(finalFile.length())
                .mimeType(file.getContentType())
                .mediaType("video")
                .storageType(MediaStorageType.LOCAL.getCode())
                .duration(duration)
                .build();
    }

    @Override
    public String getVideoUrl(String storageKey) {
        if (StrUtil.isBlank(storageKey)) {
            return null;
        }
        String prefix = storageConfigService.current().effectiveLocalPrefix();
        return prefix + "/" + storageKey;
    }

    @Override
    public boolean deleteVideo(String storageKey) {
        if (StrUtil.isBlank(storageKey)) {
            return false;
        }
        File file = new File(rootDir(), storageKey);
        return FileUtil.del(file);
    }

    /**
     * 转码为 H.264 MP4
     * <p>
     * 输出文件名加 _transcoding 后缀避免 -y 同名覆写导致源文件被清空，
     * 成功后再 rename 回标准名。
     *
     * @return 转码后的文件；FFmpeg 不可用时返回 null
     */
    private File transcode(File source) throws IOException, InterruptedException {
        Process check = new ProcessBuilder("ffmpeg", "-version").redirectErrorStream(true).start();
        if (check.waitFor() != 0) {
            log.info("系统未安装 FFmpeg，跳过视频转码");
            return null;
        }

        String srcPath = source.getAbsolutePath();
        String baseName = FileUtil.mainName(srcPath);
        File tmpFile = new File(source.getParentFile(), baseName + "_transcoding.mp4");
        File finalFile = new File(source.getParentFile(), baseName + ".mp4");

        ProcessBuilder pb = new ProcessBuilder(
                "ffmpeg", "-y", "-i", srcPath,
                "-c:v", "libx264", "-preset", "fast", "-crf", "23",
                "-c:a", "aac", "-movflags", "+faststart",
                tmpFile.getAbsolutePath()
        );
        pb.redirectErrorStream(true);
        Process process = pb.start();
        process.getInputStream().readAllBytes();
        int exit = process.waitFor();
        if (exit != 0) {
            FileUtil.del(tmpFile);
            throw new IOException("FFmpeg 转码失败，exit=" + exit);
        }

        // 转码成功：删源文件，rename 临时文件为最终名
        FileUtil.del(source);
        if (finalFile.exists()) {
            FileUtil.del(finalFile);
        }
        if (!tmpFile.renameTo(finalFile)) {
            // renameTo 跨分区失败时走 copy
            FileUtil.move(tmpFile, finalFile, true);
        }
        return finalFile;
    }

    private Integer probeDuration(String filePath) {
        try {
            ProcessBuilder pb = new ProcessBuilder(
                    "ffprobe", "-v", "error",
                    "-show_entries", "format=duration",
                    "-of", "default=noprint_wrappers=1:nokey=1", filePath
            );
            pb.redirectErrorStream(true);
            Process p = pb.start();
            String out = new String(p.getInputStream().readAllBytes()).trim();
            p.waitFor();
            if (StrUtil.isNotBlank(out)) {
                return (int) Math.round(Double.parseDouble(out));
            }
        } catch (Exception e) {
            log.debug("获取视频时长失败", e);
        }
        return null;
    }
}
