package com.trace.product.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.trace.product.annotation.AuditLog;
import com.trace.product.annotation.RequirePerm;
import com.trace.product.common.Result;
import com.trace.product.dto.BatchGenerateDTO;
import com.trace.product.dto.BatchTaskProgress;
import com.trace.product.dto.CodeBatchOpDTO;
import com.trace.product.dto.NormalCodeVO;
import com.trace.product.entity.BatchCodeTask;
import com.trace.product.mapper.BatchCodeTaskMapper;
import com.trace.product.service.ProductCodeService;
import com.trace.product.service.ProductCodeService.BatchCsvFile;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.LinkedHashMap;

/**
 * 二维码生成接口
 */
@RequirePerm("code")
@RestController
@RequestMapping("/admin/code")
@RequiredArgsConstructor
public class ProductCodeController {

    private final ProductCodeService productCodeService;
    private final BatchCodeTaskMapper batchCodeTaskMapper;

    /**
     * 生成普通产品码（Hutool/ZXing 生成纯 H5 短链二维码，不依赖微信接口）
     */
    @AuditLog(module = "二维码", action = "生成普通码")
    @PostMapping("/normal/{productId}")
    public Result<NormalCodeVO> generateNormal(@PathVariable Long productId) {
        return Result.success(productCodeService.generateNormalCode(productId));
    }

    /**
     * 启动一物一码批量生成（异步，立即返回 taskId）
     */
    @AuditLog(module = "二维码", action = "批量生成")
    @PostMapping("/batch")
    public Result<BatchTaskProgress> batchGenerate(@Valid @RequestBody BatchGenerateDTO dto) {
        return Result.success(productCodeService.startBatch(dto));
    }

    /**
     * 查询批量生成进度
     */
    @GetMapping("/batch/{taskId}")
    public Result<BatchTaskProgress> progress(@PathVariable String taskId) {
        return Result.success(productCodeService.getProgress(taskId));
    }

    /**
     * 码列表分页（按产品 / 类型 / 批次筛选，JOIN 产品表带出基础信息）
     */
    @GetMapping("/page")
    public Result<Page<com.trace.product.dto.CodeWithProductVO>> page(
            @RequestParam(defaultValue = "1") long current,
            @RequestParam(defaultValue = "10") long size,
            @RequestParam(required = false) Long productId,
            @RequestParam(required = false) Integer codeType,
            @RequestParam(required = false) String batchNo,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Integer status) {
        return Result.success(productCodeService.pageWithProduct(
                current, size, productId, codeType, batchNo, keyword, status));
    }

    /**
     * 作废 / 恢复单个码
     */
    @AuditLog(module = "二维码", action = "作废/恢复")
    @PostMapping("/{id}/status")
    public Result<Void> updateStatus(@PathVariable Long id, @RequestParam Integer status) {
        productCodeService.updateStatus(id, status);
        return Result.success();
    }

    /**
     * 删除单个码（逻辑删除）
     */
    @AuditLog(module = "二维码", action = "删除")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        productCodeService.deleteCode(id);
        return Result.success();
    }

    /**
     * 批量作废 / 恢复
     */
    @AuditLog(module = "二维码", action = "批量作废/恢复")
    @PostMapping("/batch/status")
    public Result<Map<String, Integer>> batchStatus(@Valid @RequestBody CodeBatchOpDTO dto) {
        int cnt = productCodeService.batchUpdateStatus(dto.getIds(), dto.getStatus());
        Map<String, Integer> data = new LinkedHashMap<>();
        data.put("count", cnt);
        return Result.success(data);
    }

    /**
     * 批量删除（逻辑删除）
     */
    @AuditLog(module = "二维码", action = "批量删除")
    @PostMapping("/batch/delete")
    public Result<Map<String, Integer>> batchDelete(@Valid @RequestBody CodeBatchOpDTO dto) {
        int cnt = productCodeService.batchDelete(dto.getIds());
        Map<String, Integer> data = new LinkedHashMap<>();
        data.put("count", cnt);
        return Result.success(data);
    }

    /**
     * 为已有二维码补生成小程序码图片（普通码 / 一物一码通用）
     */
    @AuditLog(module = "二维码", action = "补生成图片")
    @PostMapping("/qr/{codeId}")
    public Result<Map<String, String>> generateQrImage(@PathVariable Long codeId) {
        String url = productCodeService.generateQrImage(codeId);
        Map<String, String> data = new LinkedHashMap<>();
        data.put("qrImageUrl", url);
        return Result.success(data);
    }

    /**
     * 历史批量任务列表（DB 持久化，即使 Redis 丢了也能看到）
     */
    @GetMapping("/batch/list")
    public Result<Page<BatchCodeTask>> batchTaskList(
            @RequestParam(defaultValue = "1") long current,
            @RequestParam(defaultValue = "20") long size,
            @RequestParam(required = false) String batchNo) {
        LambdaQueryWrapper<BatchCodeTask> qw = new LambdaQueryWrapper<>();
        if (batchNo != null && !batchNo.isBlank()) {
            qw.like(BatchCodeTask::getBatchNo, batchNo);
        }
        qw.orderByDesc(BatchCodeTask::getCreateTime);
        return Result.success(batchCodeTaskMapper.selectPage(new Page<>(current, size), qw));
    }

    /**
     * 下载一物一码 CSV（生成完成后可用）
     */
    @GetMapping("/batch/{taskId}/download")
    public ResponseEntity<Resource> download(@PathVariable String taskId) {
        BatchCsvFile csv = productCodeService.getCsvFile(taskId);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType("text/csv;charset=UTF-8"));
        headers.setContentDisposition(ContentDisposition.attachment()
                .filename(csv.fileName(), StandardCharsets.UTF_8)
                .build());
        headers.setContentLength(csv.file().length());
        Resource resource = new FileSystemResource(csv.file());
        return ResponseEntity.ok().headers(headers).body(resource);
    }
}
