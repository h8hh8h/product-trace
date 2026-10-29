package com.trace.product.dto;

import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

/**
 * 二维码批量操作参数（批量作废/恢复/删除）
 */
@Data
public class CodeBatchOpDTO {

    @NotEmpty(message = "请选择要操作的二维码")
    private List<Long> ids;

    /** 仅批量改状态时使用：1-有效 0-作废 */
    private Integer status;
}
