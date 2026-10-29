package com.trace.product.controller;

import com.trace.product.common.Result;
import com.trace.product.entity.SysDict;
import com.trace.product.service.DictService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 基础数据字典接口（品牌 / 分类）
 */
@RestController
@RequestMapping("/admin/dict")
@RequiredArgsConstructor
public class DictController {

    private final DictService dictService;

    /**
     * 按类型查询字典项
     */
    @GetMapping
    public Result<List<SysDict>> list(@RequestParam String type) {
        return Result.success(dictService.list(type));
    }

    /**
     * 新增字典项
     */
    @PostMapping
    public Result<SysDict> add(@RequestParam String type, @RequestParam String name) {
        return Result.success(dictService.add(type, name));
    }

    /**
     * 删除字典项
     */
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        dictService.delete(id);
        return Result.success();
    }
}
