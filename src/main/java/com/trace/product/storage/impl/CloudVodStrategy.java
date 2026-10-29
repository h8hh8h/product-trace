package com.trace.product.storage.impl;

import com.trace.product.storage.MediaStorageType;
import com.trace.product.storage.MediaUploadResult;
import com.trace.product.storage.VideoStrategy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

/**
 * 云点播视频存储策略（预留）
 * <p>
 * 后期接入阿里云 VOD / 腾讯云点播时在此实现，
 * 视频转码与 CDN 分发交由云厂商托管。
 */
@Slf4j
@Component
public class CloudVodStrategy implements VideoStrategy {

    @Override
    public MediaStorageType getType() {
        return MediaStorageType.VOD;
    }

    @Override
    public MediaUploadResult uploadVideo(MultipartFile file, String dir) {
        throw new UnsupportedOperationException("云点播视频存储尚未实现，请将 media.storage.type 改为 local");
    }

    @Override
    public String getVideoUrl(String storageKey) {
        throw new UnsupportedOperationException("云点播视频存储尚未实现");
    }

    @Override
    public boolean deleteVideo(String storageKey) {
        throw new UnsupportedOperationException("云点播视频存储尚未实现");
    }
}
