package com.trace.product.controller;

import com.trace.product.annotation.AuditLog;
import com.trace.product.annotation.RequirePerm;
import com.trace.product.common.Result;
import com.trace.product.service.ProductImportService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * 产品批量导入（Excel）
 */
@RequirePerm("product")
@RestController
@RequestMapping("/admin/product-import")
@RequiredArgsConstructor
public class ProductImportController {

    private final ProductImportService productImportService;

    /**
     * 下载导入模板（内存生成 xlsx，流式返回）
     */
    @GetMapping("/template")
    public ResponseEntity<byte[]> template() {
        byte[] bytes = productImportService.buildTemplate();
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType(
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));
        headers.setContentDisposition(ContentDisposition.attachment()
                .filename("产品导入模板.xlsx", StandardCharsets.UTF_8)
                .build());
        headers.setContentLength(bytes.length);
        return ResponseEntity.ok().headers(headers).body(bytes);
    }

    /**
     * 上传 Excel 批量导入产品，返回行级结果
     */
    @AuditLog(module = "product", action = "批量导入")
    @PostMapping
    public Result<Map<String, Object>> importProducts(@RequestParam("file") MultipartFile file) {
        return Result.success(productImportService.importProducts(file));
    }
}
