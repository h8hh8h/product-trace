package com.trace.product.util;

import cn.hutool.core.util.IdUtil;
import org.springframework.stereotype.Component;

/**
 * 一物一码 / 普通码码值生成器
 * <p>
 * 12 位大写十六进制短码（UUID 截取），兼顾长度与碰撞概率。
 */
@Component
public class CodeGenerator {

    private static final int LENGTH = 12;

    public String next() {
        return IdUtil.fastSimpleUUID().substring(0, LENGTH).toUpperCase();
    }
}
