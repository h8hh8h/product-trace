package com.trace.product.service;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.trace.product.common.BusinessException;
import com.trace.product.common.ResultCode;
import com.trace.product.dto.ProductSaveDTO;
import com.trace.product.entity.Product;
import com.trace.product.mapper.ProductMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 产品管理
 */
@Service
@RequiredArgsConstructor
public class ProductService {

    private final ProductMapper productMapper;

    public Product create(ProductSaveDTO dto) {
        checkProductNoUnique(dto.getProductNo(), null);
        Product product = new Product();
        BeanUtil.copyProperties(dto, product);
        product.setId(null);
        product.setViewCount(0L);
        product.setDeleted(0);
        productMapper.insert(product);
        return product;
    }

    public Product update(ProductSaveDTO dto) {
        if (dto.getId() == null) {
            throw new BusinessException("产品 ID 不能为空");
        }
        Product existing = getById(dto.getId());
        checkProductNoUnique(dto.getProductNo(), dto.getId());
        BeanUtil.copyProperties(dto, existing);
        productMapper.updateById(existing);
        return existing;
    }

    public void delete(Long id) {
        getById(id);
        productMapper.deleteById(id);
    }

    public Product getById(Long id) {
        Product product = productMapper.selectById(id);
        if (product == null) {
            throw new BusinessException(ResultCode.NOT_FOUND.getCode(), "产品不存在");
        }
        return product;
    }

    public Page<Product> page(long current, long size, String keyword, Integer shelfStatus) {
        LambdaQueryWrapper<Product> wrapper = new LambdaQueryWrapper<>();
        if (StrUtil.isNotBlank(keyword)) {
            wrapper.and(w -> w.like(Product::getProductName, keyword)
                    .or().like(Product::getProductNo, keyword)
                    .or().like(Product::getCategory, keyword)
                    .or().like(Product::getBrand, keyword)
                    .or().like(Product::getSpec, keyword)
                    .or().like(Product::getThickness, keyword)
                    .or().like(Product::getColor, keyword)
                    .or().like(Product::getSize, keyword)
                    .or().like(Product::getFeature, keyword)
                    .or().like(Product::getCrotchType, keyword)
                    .or().like(Product::getMaterial, keyword)
                    .or().like(Product::getComposition, keyword));
        }
        if (shelfStatus != null) {
            wrapper.eq(Product::getShelfStatus, shelfStatus);
        }
        wrapper.orderByDesc(Product::getSortOrder).orderByDesc(Product::getId);
        return productMapper.selectPage(new Page<>(current, size), wrapper);
    }

    private void checkProductNoUnique(String productNo, Long excludeId) {
        LambdaQueryWrapper<Product> wrapper = new LambdaQueryWrapper<Product>()
                .eq(Product::getProductNo, productNo);
        if (excludeId != null) {
            wrapper.ne(Product::getId, excludeId);
        }
        if (productMapper.selectCount(wrapper) > 0) {
            throw new BusinessException("产品编号已存在: " + productNo);
        }
    }
}
