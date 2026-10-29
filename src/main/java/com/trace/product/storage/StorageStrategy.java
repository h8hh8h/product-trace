package com.trace.product.storage;

import org.springframework.web.multipart.MultipartFile;

/**
 * 图片存储策略接口（策略模式）
 * <p>
 * 本地 / OSS 等实现类必须实现该接口。
 * 后期切换存储只需修改配置 media.storage.type，业务代码无需改动。
 */
public interface StorageStrategy {

    /**
     * 当前策略类型
     */
    MediaStorageType getType();

    /**
     * 上传图片
     *
     * @param file 图片文件
     * @param dir  业务目录，如 product/cover，可为空
     * @return 上传结果（含 storageKey 与访问 URL）
     */
    MediaUploadResult uploadImage(MultipartFile file, String dir);

    /**
     * 上传图片（字节数组，如动态生成的二维码图片）
     *
     * @param data     图片字节
     * @param fileName 原始文件名（用于提取扩展名）
     * @param dir      业务目录
     * @return 上传结果
     */
    MediaUploadResult uploadImageBytes(byte[] data, String fileName, String dir);

    /**
     * 根据 storageKey 获取图片访问 URL
     */
    String getImageUrl(String storageKey);

    /**
     * 删除图片
     */
    boolean deleteImage(String storageKey);
}
