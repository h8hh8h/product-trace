package com.trace.product.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 站点/首页配置（键值对）
 */
@Data
@TableName("sys_site_config")
public class SysSiteConfig {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 配置项键，如 home_title / home_banner */
    private String configKey;

    /** 值（纯文本或 JSON） */
    private String configValue;

    private LocalDateTime updateTime;
}
