package com.trace.product.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.trace.product.annotation.AuditLog;
import com.trace.product.annotation.RequirePerm;
import com.trace.product.common.BusinessException;
import com.trace.product.common.Result;
import com.trace.product.entity.MediaFile;
import com.trace.product.service.MediaFileService;
import com.trace.product.storage.MediaStrategyFactory;
import com.trace.product.storage.MediaUploadResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.Set;

/**
 * 媒体上传接口（产品富文本配图等）
 * <p>
 * 规格限制：
 *   图片：JPG / PNG / WebP，单文件 ≤ 2 MB
 *   视频：MP4 / WebM，单文件 ≤ 50 MB
 */
@Slf4j
@RequirePerm("media")
@RestController
@RequestMapping("/admin/media")
@RequiredArgsConstructor
public class MediaController {

    private static final Set<String> IMAGE_ALLOWED = Set.of("jpg", "jpeg", "png", "webp");
    private static final Set<String> VIDEO_ALLOWED  = Set.of("mp4", "webm");
    private static final long IMAGE_MAX_BYTES = 2L * 1024 * 1024;      // 2 MB
    private static final long VIDEO_MAX_BYTES = 50L * 1024 * 1024;     // 50 MB

    private final MediaStrategyFactory mediaStrategyFactory;
    private final MediaFileService mediaFileService;

    /**
     * 媒体库分页检索：type=image/video/all，keyword 按文件名/存储 key 模糊
     */
    @GetMapping("/list")
    public Result<Page<MediaFile>> list(@RequestParam(defaultValue = "all") String type,
                                        @RequestParam(required = false) String keyword,
                                        @RequestParam(defaultValue = "1") long page,
                                        @RequestParam(defaultValue = "24") long size) {
        return Result.success(mediaFileService.page(type, keyword, page, size));
    }

    /**
     * 上传图片，返回可访问 URL（同时落库到媒体库）
     */
    @AuditLog(module = "媒体", action = "上传图片")
    @PostMapping("/image")
    public Result<MediaUploadResult> uploadImage(@RequestParam("file") MultipartFile file,
                                                 @RequestParam(required = false) String dir) {
        validateImage(file);
        MediaUploadResult result = mediaStrategyFactory.getStorageStrategy().uploadImage(file, dir);
        mediaFileService.record(result);
        return Result.success(result);
    }

    /**
     * 上传视频（产品视频），返回可访问 URL（同时落库到媒体库）
     */
    @AuditLog(module = "媒体", action = "上传视频")
    @PostMapping("/video")
    public Result<MediaUploadResult> uploadVideo(@RequestParam("file") MultipartFile file,
                                                 @RequestParam(required = false) String dir) {
        validateVideo(file);
        MediaUploadResult result = mediaStrategyFactory.getVideoStrategy().uploadVideo(file, dir);
        mediaFileService.record(result);
        return Result.success(result);
    }

    // ========== 文件校验 ==========

    private void validateImage(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException("请选择要上传的图片文件");
        }
        String ext = getExt(file.getOriginalFilename());
        if (!IMAGE_ALLOWED.contains(ext)) {
            throw new BusinessException("图片格式不支持，仅允许 JPG / PNG / WebP（当前：" + ext + "）");
        }
        if (file.getSize() > IMAGE_MAX_BYTES) {
            throw new BusinessException("图片大小超过 2 MB（当前：" + (file.getSize() / 1024) + " KB），请先压缩再上传");
        }
    }

    private void validateVideo(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException("请选择要上传的视频文件");
        }
        String ext = getExt(file.getOriginalFilename());
        if (!VIDEO_ALLOWED.contains(ext)) {
            throw new BusinessException("视频格式不支持，仅允许 MP4 / WebM（当前：" + ext + "）");
        }
        if (file.getSize() > VIDEO_MAX_BYTES) {
            throw new BusinessException("视频大小超过 50 MB（当前：" + (file.getSize() / 1024 / 1024) + " MB），请先压缩到 1080P 以下再上传");
        }
    }

    private String getExt(String filename) {
        if (filename == null) return "";
        int idx = filename.lastIndexOf('.');
        if (idx < 0) return "";
        return filename.substring(idx + 1).toLowerCase();
    }
}
