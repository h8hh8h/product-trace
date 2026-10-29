package com.trace.product.storage;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 媒体存储类型
 */
@Getter
@AllArgsConstructor
public enum MediaStorageType {

    LOCAL("local", "本地存储"),
    OSS("oss", "对象存储"),
    VOD("vod", "云点播");

    private final String code;
    private final String desc;

    public static MediaStorageType of(String code) {
        for (MediaStorageType type : values()) {
            if (type.code.equalsIgnoreCase(code)) {
                return type;
            }
        }
        throw new IllegalArgumentException("不支持的存储类型: " + code);
    }
}
