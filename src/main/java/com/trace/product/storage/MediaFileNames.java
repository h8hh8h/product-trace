package com.trace.product.storage;

import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;

import java.io.File;
import java.util.regex.Pattern;

/**
 * 媒体文件命名：优先沿用前端传入的语义化文件名（如 S8011_1729912345.jpg），
 * 原始名含不安全字符（中文/空格/特殊符号）或超长时回退 UUID。
 */
public final class MediaFileNames {

    /** 安全文件名白名单：字母数字下划线连字符点（ASCII，URL 友好） */
    private static final Pattern SAFE = Pattern.compile("^[A-Za-z0-9._-]{1,100}$");

    private MediaFileNames() {
    }

    /**
     * @param originalName 上游传入的文件名（可空）
     * @param ext          扩展名（小写，不含点，必填）
     * @return 最终落盘/对象名
     */
    public static String resolve(String originalName, String ext) {
        // 去掉可能携带的路径部分（subAfter 在分隔符不存在时返回空串，必须先判断包含）
        String name = StrUtil.nullToEmpty(originalName);
        if (name.contains("/")) name = StrUtil.subAfter(name, "/", true);
        if (name.contains("\\")) name = StrUtil.subAfter(name, "\\", true);
        if (StrUtil.isBlank(name) || !SAFE.matcher(name).matches()) {
            return IdUtil.fastSimpleUUID() + "." + ext;
        }
        // 统一扩展名
        String nameExt = StrUtil.subAfter(name, '.', true).toLowerCase();
        if (!nameExt.equals(ext)) {
            String base = StrUtil.subBefore(name, '.', true);
            name = (StrUtil.isBlank(base) ? IdUtil.fastSimpleUUID() : base) + "." + ext;
        }
        if (name.length() > 100) {
            return IdUtil.fastSimpleUUID() + "." + ext;
        }
        return name;
    }

    /** 本地落盘冲突兜底：目标已存在则换 UUID（同货号同毫秒的极端场景） */
    public static String avoidConflict(File destFile, String fileName, String ext) {
        return destFile.exists() ? IdUtil.fastSimpleUUID() + "." + ext : fileName;
    }
}
