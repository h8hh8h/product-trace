package com.trace.product.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.trace.product.entity.MediaFile;
import com.trace.product.mapper.MediaFileMapper;
import com.trace.product.storage.MediaUploadResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * 媒体资产库服务：上传落库（失败不影响上传主流程）+ 检索
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MediaFileService {

    private final MediaFileMapper mediaFileMapper;

    /**
     * 上传成功后落库记录。任何异常只打日志，不影响上传结果。
     */
    public void record(MediaUploadResult r) {
        try {
            if (r == null || !StringUtils.hasText(r.getStorageKey()) || !StringUtils.hasText(r.getFileUrl())) {
                return;
            }
            MediaFile f = new MediaFile();
            f.setFileName(StringUtils.hasText(r.getFileName()) ? r.getFileName() : r.getStorageKey());
            f.setStorageKey(r.getStorageKey());
            f.setFileUrl(r.getFileUrl());
            f.setMediaType(StringUtils.hasText(r.getMediaType()) ? r.getMediaType() : "other");
            f.setFileSize(r.getFileSize() == null ? 0L : r.getFileSize());
            f.setMimeType(r.getMimeType());
            f.setStorageType(r.getStorageType());
            f.setWidth(r.getWidth());
            f.setHeight(r.getHeight());
            f.setDuration(r.getDuration());
            f.setDeleted(0);
            mediaFileMapper.insert(f);
        } catch (Exception e) {
            log.warn("媒体文件落库失败（不影响上传）: storageKey={}, err={}",
                    r == null ? null : r.getStorageKey(), e.getMessage());
        }
    }

    /**
     * 媒体库分页检索：按类型 + 关键字（文件名/存储 key 模糊）
     */
    public Page<MediaFile> page(String mediaType, String keyword, long current, long size) {
        LambdaQueryWrapper<MediaFile> qw = new LambdaQueryWrapper<>();
        qw.eq(MediaFile::getDeleted, 0);
        if (StringUtils.hasText(mediaType) && !"all".equals(mediaType)) {
            qw.eq(MediaFile::getMediaType, mediaType);
        }
        if (StringUtils.hasText(keyword)) {
            String kw = keyword.trim();
            qw.and(w -> w.like(MediaFile::getFileName, kw).or().like(MediaFile::getStorageKey, kw));
        }
        qw.orderByDesc(MediaFile::getId);
        Page<MediaFile> page = new Page<>(Math.max(current, 1), Math.min(Math.max(size, 1), 100));
        return mediaFileMapper.selectPage(page, qw);
    }
}
