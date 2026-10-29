package com.trace.product.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 基础数据字典（品牌 / 分类）
 */
@Data
@TableName("sys_dict")
public class SysDict {

    /** 字典类型：品牌 */
    public static final String TYPE_BRAND = "brand";
    /** 字典类型：分类 */
    public static final String TYPE_CATEGORY = "category";

    @TableId(type = IdType.AUTO)
    private Long id;

    /** brand-品牌 category-分类 */
    private String dictType;

    private String name;

    private Integer sortOrder;

    private LocalDateTime createTime;
}
