package com.trace.product.service;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.trace.product.common.BusinessException;
import com.trace.product.common.ResultCode;
import com.trace.product.entity.SysDict;
import com.trace.product.mapper.SysDictMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;

/**
 * 基础数据字典（品牌 / 分类）维护
 */
@Service
@RequiredArgsConstructor
public class DictService {

    private static final Set<String> TYPES = Set.of(SysDict.TYPE_BRAND, SysDict.TYPE_CATEGORY);

    private final SysDictMapper dictMapper;

    public List<SysDict> list(String type) {
        checkType(type);
        return dictMapper.selectList(new LambdaQueryWrapper<SysDict>()
                .eq(SysDict::getDictType, type)
                .orderByDesc(SysDict::getSortOrder)
                .orderByAsc(SysDict::getId));
    }

    public SysDict add(String type, String name) {
        checkType(type);
        if (StrUtil.isBlank(name)) {
            throw new BusinessException("名称不能为空");
        }
        String value = name.trim();
        Long exists = dictMapper.selectCount(new LambdaQueryWrapper<SysDict>()
                .eq(SysDict::getDictType, type)
                .eq(SysDict::getName, value));
        if (exists != null && exists > 0) {
            throw new BusinessException("该名称已存在：" + value);
        }
        SysDict dict = new SysDict();
        dict.setDictType(type);
        dict.setName(value);
        dict.setSortOrder(0);
        dictMapper.insert(dict);
        return dict;
    }

    public void delete(Long id) {
        SysDict dict = dictMapper.selectById(id);
        if (dict == null) {
            throw new BusinessException(ResultCode.NOT_FOUND.getCode(), "字典项不存在");
        }
        dictMapper.deleteById(id);
    }

    private void checkType(String type) {
        if (!TYPES.contains(type)) {
            throw new BusinessException("字典类型只能是 brand 或 category");
        }
    }
}
