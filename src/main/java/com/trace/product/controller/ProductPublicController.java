package com.trace.product.controller;

import com.trace.product.common.BusinessException;
import com.trace.product.common.Result;
import com.trace.product.common.ResultCode;
import com.trace.product.entity.Product;
import com.trace.product.service.ProductService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 用户端产品接口（无需登录）
 */
@RestController
@RequestMapping("/product")
@RequiredArgsConstructor
public class ProductPublicController {

    private final ProductService productService;

    /**
     * 产品详情：仅返回已上架产品
     */
    @GetMapping("/{id}")
    public Result<Product> detail(@PathVariable Long id) {
        Product product = productService.getById(id);
        if (product.getShelfStatus() == null || product.getShelfStatus() != 1) {
            throw new BusinessException(ResultCode.PRODUCT_OFFLINE);
        }
        return Result.success(product);
    }
}
