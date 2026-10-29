package com.trace.product.dto;

import lombok.Data;

/**
 * 通用统计项（分类/品牌/来源/日期 分组计数）
 */
@Data
public class StatItemVO {

    /** 分组名称 */
    private String name;

    /** 数量 */
    private Long cnt;
}
