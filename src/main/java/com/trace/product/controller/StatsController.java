package com.trace.product.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.trace.product.annotation.RequirePerm;
import com.trace.product.common.Result;
import com.trace.product.dto.StatItemVO;
import com.trace.product.entity.Product;
import com.trace.product.entity.ProductCode;
import com.trace.product.entity.ScanRecord;
import com.trace.product.mapper.ProductCodeMapper;
import com.trace.product.mapper.ProductMapper;
import com.trace.product.mapper.ScanRecordMapper;
import com.trace.product.mapper.StatsMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 后台数据统计接口
 */
@RequirePerm("stats")
@RestController
@RequestMapping("/admin/stats")
@RequiredArgsConstructor
public class StatsController {

    private final ProductMapper productMapper;
    private final ProductCodeMapper productCodeMapper;
    private final ScanRecordMapper scanRecordMapper;
    private final StatsMapper statsMapper;

    /**
     * 总览卡片：产品 / 二维码 / 扫码 / 预警 多维度明细
     */
    @GetMapping("/overview")
    public Result<Map<String, Object>> overview() {
        LocalDateTime todayStart = LocalDate.now().atStartOfDay();
        LocalDateTime yesterdayStart = todayStart.minusDays(1);

        long productCount = productMapper.selectCount(null);
        long onlineCount = productMapper.selectCount(
                new LambdaQueryWrapper<Product>().eq(Product::getShelfStatus, 1));
        long todayProductCount = productMapper.selectCount(
                new LambdaQueryWrapper<Product>().ge(Product::getCreateTime, todayStart));

        long codeCount = productCodeMapper.selectCount(null);
        long normalCodeCount = productCodeMapper.selectCount(
                new LambdaQueryWrapper<ProductCode>().eq(ProductCode::getCodeType, ProductCode.TYPE_NORMAL));
        long uniqueCodeCount = productCodeMapper.selectCount(
                new LambdaQueryWrapper<ProductCode>().eq(ProductCode::getCodeType, ProductCode.TYPE_UNIQUE));
        long activeCodeCount = productCodeMapper.selectCount(
                new LambdaQueryWrapper<ProductCode>().eq(ProductCode::getStatus, 1));
        long todayCodeCount = productCodeMapper.selectCount(
                new LambdaQueryWrapper<ProductCode>().ge(ProductCode::getCreateTime, todayStart));
        long scannedCodeCount = statsMapper.scannedCodeCount();

        long totalScan = scanRecordMapper.selectCount(null);
        long todayScan = scanRecordMapper.selectCount(
                new LambdaQueryWrapper<ScanRecord>().ge(ScanRecord::getScanTime, todayStart));
        long yesterdayScan = scanRecordMapper.selectCount(new LambdaQueryWrapper<ScanRecord>()
                .ge(ScanRecord::getScanTime, yesterdayStart).lt(ScanRecord::getScanTime, todayStart));
        long fakeCount = scanRecordMapper.selectCount(
                new LambdaQueryWrapper<ScanRecord>().eq(ScanRecord::getIsFake, 1));
        long todayFakeCount = scanRecordMapper.selectCount(new LambdaQueryWrapper<ScanRecord>()
                .ge(ScanRecord::getScanTime, todayStart).eq(ScanRecord::getIsFake, 1));

        Map<String, Object> data = new LinkedHashMap<>();
        // 产品
        data.put("productCount", productCount);
        data.put("onlineCount", onlineCount);
        data.put("offlineCount", productCount - onlineCount);
        data.put("todayProductCount", todayProductCount);
        // 二维码
        data.put("codeCount", codeCount);
        data.put("normalCodeCount", normalCodeCount);
        data.put("uniqueCodeCount", uniqueCodeCount);
        data.put("activeCodeCount", activeCodeCount);
        data.put("invalidCodeCount", codeCount - activeCodeCount);
        data.put("scannedCodeCount", scannedCodeCount);
        data.put("unscannedCodeCount", Math.max(0, codeCount - scannedCodeCount));
        data.put("todayCodeCount", todayCodeCount);
        // 扫码
        data.put("totalScan", totalScan);
        data.put("todayScan", todayScan);
        data.put("yesterdayScan", yesterdayScan);
        data.put("fakeCount", fakeCount);
        data.put("todayFakeCount", todayFakeCount);
        data.put("viewCount", statsMapper.totalViewCount());
        return Result.success(data);
    }

    /**
     * 近 N 日扫码趋势（默认 7 天）
     */
    @GetMapping("/trend")
    public Result<List<Map<String, Object>>> trend(@RequestParam(defaultValue = "7") int days) {
        int span = Math.min(Math.max(days, 1), 90);
        List<Map<String, Object>> list = new ArrayList<>();
        for (int i = span - 1; i >= 0; i--) {
            LocalDate day = LocalDate.now().minusDays(i);
            LocalDateTime start = day.atStartOfDay();
            LocalDateTime end = day.plusDays(1).atStartOfDay();
            Long count = scanRecordMapper.selectCount(new LambdaQueryWrapper<ScanRecord>()
                    .ge(ScanRecord::getScanTime, start).lt(ScanRecord::getScanTime, end));
            Long fake = scanRecordMapper.selectCount(new LambdaQueryWrapper<ScanRecord>()
                    .ge(ScanRecord::getScanTime, start).lt(ScanRecord::getScanTime, end)
                    .eq(ScanRecord::getIsFake, 1));
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("date", day.toString());
            item.put("count", count);
            item.put("fakeCount", fake);
            list.add(item);
        }
        return Result.success(list);
    }

    /**
     * 细分统计：分类分布 / 品牌分布 / 扫码来源 / 热门产品 / 近 7 日码生成趋势
     */
    @GetMapping("/detail")
    public Result<Map<String, Object>> detail() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("categoryStats", statsMapper.categoryStats());
        data.put("brandStats", statsMapper.brandStats());
        data.put("sourceStats", statsMapper.scanSourceStats());
        data.put("topProducts", statsMapper.topProducts(5));
        data.put("codeTrend", fillCodeTrend(statsMapper.codeTrend(
                LocalDate.now().minusDays(6).atStartOfDay())));
        return Result.success(data);
    }

    /**
     * 补齐近 7 日没有生成记录的日期，便于前端直接绘图
     */
    private List<StatItemVO> fillCodeTrend(List<StatItemVO> raw) {
        Map<String, Long> map = new LinkedHashMap<>();
        for (StatItemVO item : raw) {
            map.put(item.getName(), item.getCnt());
        }
        List<StatItemVO> list = new ArrayList<>();
        for (int i = 6; i >= 0; i--) {
            String day = LocalDate.now().minusDays(i).toString();
            StatItemVO vo = new StatItemVO();
            vo.setName(day);
            vo.setCnt(map.getOrDefault(day, 0L));
            list.add(vo);
        }
        return list;
    }

    /**
     * 扫码地域分布：省份占比 + 城市 Top 10
     */
    @GetMapping("/geo-distribution")
    public Result<Map<String, Object>> geoDistribution() {
        long total = scanRecordMapper.selectCount(null);
        long resolved = statsMapper.geoResolvedCount();
        List<StatItemVO> provinces = statsMapper.provinceStats();
        List<StatItemVO> cities = statsMapper.cityStats(10);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("total", total);
        data.put("resolved", resolved);
        data.put("unresolved", Math.max(0, total - resolved));
        data.put("provinces", provinces);
        data.put("cities", cities);
        return Result.success(data);
    }

    /**
     * 扫码记录分页
     */
    @GetMapping("/scan-page")
    public Result<Page<ScanRecord>> scanPage(@RequestParam(defaultValue = "1") long current,
                                             @RequestParam(defaultValue = "10") long size,
                                             @RequestParam(required = false) String code,
                                             @RequestParam(required = false) Integer isFake,
                                             @RequestParam(required = false) String province,
                                             @RequestParam(required = false)
                                             @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        LambdaQueryWrapper<ScanRecord> wrapper = new LambdaQueryWrapper<>();
        wrapper.like(code != null && !code.isBlank(), ScanRecord::getCode, code);
        wrapper.eq(isFake != null, ScanRecord::getIsFake, isFake);
        wrapper.eq(province != null && !province.isBlank(), ScanRecord::getScanProvince, province);
        if (date != null) {
            wrapper.ge(ScanRecord::getScanTime, date.atStartOfDay());
            wrapper.lt(ScanRecord::getScanTime, date.plusDays(1).atStartOfDay());
        }
        wrapper.orderByDesc(ScanRecord::getScanTime);
        return Result.success(scanRecordMapper.selectPage(new Page<>(current, size), wrapper));
    }
}
