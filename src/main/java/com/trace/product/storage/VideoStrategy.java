package com.trace.product.storage;

import org.springframework.web.multipart.MultipartFile;

/**
 * 视频存储策略接口（策略模式）
 * <p>
 * 本地 / 云点播等实现类必须实现该接口。
 * 后期切换视频存储只需修改配置 media.storage.type，业务代码无需改动。
 */
public interface VideoStrategy {

    /**
     * 当前策略类型
     */
    MediaStorageType getType();

    /**
     * 上传视频
     *
     * @param file 视频文件
     * @param dir  业务目录，如 product/video，可为空
     * @return 上传结果（含 storageKey 与访问 URL）
     */
    MediaUploadResult uploadVideo(MultipartFile file, String dir);

    /**
     * 根据 storageKey 获取视频访问 URL
     */
    String getVideoUrl(String storageKey);

    /**
     * 删除视频
     */
    boolean deleteVideo(String storageKey);
}
