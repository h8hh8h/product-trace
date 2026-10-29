package com.trace.product.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.trace.product.annotation.AuditLog;
import com.trace.product.annotation.RequirePerm;
import com.trace.product.common.Result;
import com.trace.product.dto.ProductSaveDTO;
import com.trace.product.entity.Product;
import com.trace.product.service.ProductService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 后台产品管理接口
 */
@RequirePerm("product")
@RestController
@RequestMapping("/admin/product")
@RequiredArgsConstructor
public class ProductAdminController {

    private final ProductService productService;

    /**
     * 新增产品（支持图文富文本 description）
     */
    @AuditLog(module = "产品", action = "新增")
    @PostMapping
    public Result<Product> create(@Valid @RequestBody ProductSaveDTO dto) {
        return Result.success(productService.create(dto));
    }

    /**
     * 编辑产品
     */
    @AuditLog(module = "产品", action = "编辑")
    @PutMapping
    public Result<Product> update(@Valid @RequestBody ProductSaveDTO dto) {
        return Result.success(productService.update(dto));
    }

    /**
     * 删除产品（逻辑删除）
     */
    @AuditLog(module = "产品", action = "删除")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        productService.delete(id);
        return Result.success();
    }

    /**
     * 产品详情
     */
    @GetMapping("/{id}")
    public Result<Product> detail(@PathVariable Long id) {
        return Result.success(productService.getById(id));
    }

    /**
     * 产品分页
     */
    @GetMapping("/page")
    public Result<Page<Product>> page(@RequestParam(defaultValue = "1") long current,
                                      @RequestParam(defaultValue = "10") long size,
                                      @RequestParam(required = false) String keyword,
                                      @RequestParam(required = false) Integer shelfStatus) {
        return Result.success(productService.page(current, size, keyword, shelfStatus));
    }
}
