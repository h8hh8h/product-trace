package com.trace.product.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 一物一码批量生成参数
 */
@Data
public class BatchGenerateDTO {

    @NotNull(message = "产品 ID 不能为空")
    private Long productId;

    @NotNull(message = "生成数量不能为空")
    @Min(value = 1, message = "生成数量至少为 1")
    @Max(value = 100000, message = "单批次最多生成 100000 个")
    private Integer count;

    /** 批次号，为空自动生成 */
    private String batchNo;

    /**
     * 本批次每个码的扫码次数上限（防伪阈值）。
     * 0 = 不限；默认值 3（走 trace.anti-fake.default-scan-limit）。
     */
    @Min(value = 0, message = "扫码次数上限不能为负")
    @Max(value = 999, message = "扫码次数上限最多 999")
    private Integer scanLimit;
}
