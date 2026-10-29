package com.trace.product.service;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.trace.product.common.BusinessException;
import com.trace.product.entity.Product;
import com.trace.product.entity.SysDict;
import com.trace.product.mapper.ProductMapper;
import com.trace.product.mapper.SysDictMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 产品批量导入（Excel）
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProductImportService extends ServiceImpl<ProductMapper, Product> {

    /** 上传文件大小上限：5MB */
    private static final long MAX_FILE_SIZE = 5L * 1024 * 1024;
    /** 返回给前端的行级错误上限 */
    private static final int MAX_ERRORS_RETURNED = 100;
    /** saveBatch 每批条数 */
    private static final int BATCH_SIZE = 200;

    /** 模板表头（与解析列顺序一一对应） */
    private static final String[] HEADERS = {
            "货号*", "品名*", "品牌", "分类", "裆型", "厚度", "颜色", "尺码",
            "规格", "材质", "成分", "特点", "产地", "统一零售价", "排序"
    };

    private final SysDictMapper sysDictMapper;
    private final ProductMapper productMapper;

    /**
     * 内存生成 xlsx 模板（表头 + 一行示例数据）
     */
    public byte[] buildTemplate() {
        try (Workbook wb = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = wb.createSheet("产品导入");
            Row header = sheet.createRow(0);
            for (int i = 0; i < HEADERS.length; i++) {
                header.createCell(i).setCellValue(HEADERS[i]);
                sheet.setColumnWidth(i, 14 * 256);
            }
            Row sample = sheet.createRow(1);
            String[] sampleData = {
                    "WS-001", "示例·女士连裤袜 80D", "示例品牌", "连裤袜", "加裆", "80D", "黑色", "均码",
                    "盒装/1双", "锦纶包覆纱", "锦纶 85% 氨纶 15%", "防勾丝、透气", "浙江", "39.90", "100"
            };
            for (int i = 0; i < sampleData.length; i++) {
                sample.createCell(i).setCellValue(sampleData[i]);
            }
            wb.write(out);
            return out.toByteArray();
        } catch (Exception e) {
            log.error("生成产品导入模板失败", e);
            throw new BusinessException("生成模板失败: " + e.getMessage());
        }
    }

    /**
     * 解析并校验 Excel，合法行批量入库。
     *
     * @return {total, success, failed, errors:[{row, message}]}
     */
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> importProducts(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException("请选择要上传的文件");
        }
        String filename = file.getOriginalFilename() == null ? "" : file.getOriginalFilename();
        if (!filename.toLowerCase().endsWith(".xlsx")) {
            throw new BusinessException("仅支持 .xlsx 格式文件");
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new BusinessException("文件大小不能超过 5MB");
        }

        List<RowData> rows = parse(file);
        int total = rows.size();

        // 预加载：库内已有货号、品牌/分类字典
        Set<String> existingNos = new HashSet<>(productMapper.selectList(
                        new LambdaQueryWrapper<Product>().select(Product::getProductNo))
                .stream().map(Product::getProductNo).collect(Collectors.toSet()));
        Set<String> brandNames = dictNames(SysDict.TYPE_BRAND);
        Set<String> categoryNames = dictNames(SysDict.TYPE_CATEGORY);

        List<Product> valid = new ArrayList<>();
        List<Map<String, Object>> errors = new ArrayList<>();
        int failed = 0;
        Set<String> fileNos = new HashSet<>();

        for (RowData rd : rows) {
            StringBuilder msg = new StringBuilder();
            if (StrUtil.isBlank(rd.productNo)) {
                msg.append("货号必填；");
            } else if (existingNos.contains(rd.productNo) || fileNos.contains(rd.productNo)) {
                msg.append("货号重复：").append(rd.productNo).append('；');
            }
            if (StrUtil.isBlank(rd.productName)) {
                msg.append("品名必填；");
            }
            if (StrUtil.isNotBlank(rd.brand) && !brandNames.contains(rd.brand)) {
                msg.append("品牌不存在于字典：").append(rd.brand).append('；');
            }
            if (StrUtil.isNotBlank(rd.category) && !categoryNames.contains(rd.category)) {
                msg.append("分类不存在于字典：").append(rd.category).append('；');
            }
            BigDecimal price = null;
            if (StrUtil.isNotBlank(rd.retailPrice)) {
                try {
                    price = new BigDecimal(rd.retailPrice.trim());
                } catch (NumberFormatException e) {
                    msg.append("统一零售价须为数字：").append(rd.retailPrice).append('；');
                }
            }
            Integer sortOrder = null;
            if (StrUtil.isNotBlank(rd.sortOrder)) {
                try {
                    sortOrder = new BigDecimal(rd.sortOrder.trim()).intValueExact();
                } catch (NumberFormatException | ArithmeticException e) {
                    msg.append("排序须为整数：").append(rd.sortOrder).append('；');
                }
            }

            // 字段长度校验（上限与 product 表 DDL 一致，防止 Data truncation 整批回滚）
            checkLen(msg, "货号", rd.productNo, 64);
            checkLen(msg, "品名", rd.productName, 128);
            checkLen(msg, "品牌", rd.brand, 64);
            checkLen(msg, "分类", rd.category, 64);
            checkLen(msg, "裆型", rd.crotchType, 50);
            checkLen(msg, "厚度", rd.thickness, 50);
            checkLen(msg, "颜色", rd.color, 50);
            checkLen(msg, "尺码", rd.size, 50);
            checkLen(msg, "规格", rd.spec, 128);
            checkLen(msg, "材质", rd.material, 100);
            checkLen(msg, "成分", rd.composition, 255);
            checkLen(msg, "特点", rd.feature, 255);
            checkLen(msg, "产地", rd.origin, 256);

            if (msg.length() > 0) {
                failed++;
                if (errors.size() < MAX_ERRORS_RETURNED) {
                    Map<String, Object> err = new LinkedHashMap<>();
                    err.put("row", rd.rowIndex);
                    err.put("message", msg.substring(0, msg.length() - 1));
                    errors.add(err);
                }
                continue;
            }

            fileNos.add(rd.productNo);
            Product p = new Product();
            p.setProductNo(rd.productNo);
            p.setProductName(rd.productName);
            p.setBrand(StrUtil.blankToDefault(rd.brand, null));
            p.setCategory(StrUtil.blankToDefault(rd.category, null));
            p.setCrotchType(StrUtil.blankToDefault(rd.crotchType, null));
            p.setThickness(StrUtil.blankToDefault(rd.thickness, null));
            p.setColor(StrUtil.blankToDefault(rd.color, null));
            p.setSize(StrUtil.blankToDefault(rd.size, null));
            p.setSpec(StrUtil.blankToDefault(rd.spec, null));
            p.setMaterial(StrUtil.blankToDefault(rd.material, null));
            p.setComposition(StrUtil.blankToDefault(rd.composition, null));
            p.setFeature(StrUtil.blankToDefault(rd.feature, null));
            p.setOrigin(StrUtil.blankToDefault(rd.origin, null));
            p.setRetailPrice(price);
            p.setSortOrder(sortOrder);
            p.setShelfStatus(1);
            p.setViewCount(0L);
            p.setDeleted(0);
            valid.add(p);
        }

        if (!valid.isEmpty()) {
            saveBatch(valid, BATCH_SIZE);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("total", total);
        result.put("success", valid.size());
        result.put("failed", failed);
        result.put("errors", errors);
        return result;
    }

    private Set<String> dictNames(String type) {
        return sysDictMapper.selectList(new LambdaQueryWrapper<SysDict>()
                        .select(SysDict::getName)
                        .eq(SysDict::getDictType, type))
                .stream().map(SysDict::getName).collect(Collectors.toSet());
    }

    /** 字符串字段长度校验：超限记入行级错误（MySQL VARCHAR 按字符计） */
    private static void checkLen(StringBuilder msg, String label, String value, int max) {
        if (value != null && value.length() > max) {
            msg.append(label).append("超长（限").append(max).append("字符，当前")
                    .append(value.length()).append("字符）；");
        }
    }

    /**
     * 逐行解析 Excel（跳过表头，跳过完全空白行）
     */
    private List<RowData> parse(MultipartFile file) {
        List<RowData> rows = new ArrayList<>();
        DataFormatter formatter = new DataFormatter();
        try (Workbook wb = new XSSFWorkbook(file.getInputStream())) {
            Sheet sheet = wb.getSheetAt(0);
            if (sheet == null) {
                throw new BusinessException("Excel 内容为空");
            }
            int lastRow = sheet.getLastRowNum();
            for (int i = 1; i <= lastRow; i++) {
                Row row = sheet.getRow(i);
                RowData rd = new RowData();
                rd.rowIndex = i + 1; // Excel 行号（1 起，表头为第 1 行）
                if (row != null) {
                    rd.productNo = cell(formatter, row, 0);
                    rd.productName = cell(formatter, row, 1);
                    rd.brand = cell(formatter, row, 2);
                    rd.category = cell(formatter, row, 3);
                    rd.crotchType = cell(formatter, row, 4);
                    rd.thickness = cell(formatter, row, 5);
                    rd.color = cell(formatter, row, 6);
                    rd.size = cell(formatter, row, 7);
                    rd.spec = cell(formatter, row, 8);
                    rd.material = cell(formatter, row, 9);
                    rd.composition = cell(formatter, row, 10);
                    rd.feature = cell(formatter, row, 11);
                    rd.origin = cell(formatter, row, 12);
                    rd.retailPrice = cell(formatter, row, 13);
                    rd.sortOrder = cell(formatter, row, 14);
                }
                // 完全空白行跳过（不计入 total）
                if (StrUtil.isAllBlank(rd.productNo, rd.productName, rd.brand, rd.category,
                        rd.crotchType, rd.thickness, rd.color, rd.size, rd.spec, rd.material,
                        rd.composition, rd.feature, rd.origin, rd.retailPrice, rd.sortOrder)) {
                    continue;
                }
                rows.add(rd);
            }
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("解析产品导入 Excel 失败", e);
            throw new BusinessException("Excel 解析失败，请确认使用下载的模板填写: " + e.getMessage());
        }
        return rows;
    }

    private String cell(DataFormatter formatter, Row row, int idx) {
        Cell c = row.getCell(idx);
        if (c == null) {
            return null;
        }
        if (c.getCellType() == CellType.NUMERIC) {
            // 数值单元格按 BigDecimal 原样取，避免科学计数法 / 多余小数点
            String s = BigDecimal.valueOf(c.getNumericCellValue()).stripTrailingZeros().toPlainString();
            return StrUtil.blankToDefault(s, null);
        }
        return StrUtil.blankToDefault(formatter.formatCellValue(c).trim(), null);
    }

    /** 一行原始数据 */
    private static class RowData {
        int rowIndex;
        String productNo;
        String productName;
        String brand;
        String category;
        String crotchType;
        String thickness;
        String color;
        String size;
        String spec;
        String material;
        String composition;
        String feature;
        String origin;
        String retailPrice;
        String sortOrder;
    }
}
