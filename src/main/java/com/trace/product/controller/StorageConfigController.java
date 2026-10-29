package com.trace.product.controller;

import com.trace.product.annotation.AuditLog;
import com.trace.product.annotation.RequirePerm;
import com.trace.product.common.Result;
import com.trace.product.entity.SysStorageConfig;
import com.trace.product.service.StorageConfigService;
import com.trace.product.storage.impl.OssStorageStrategy;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 存储配置（后台可视化切换本地 / 阿里云 OSS）
 * <p>
 * 保存后立即生效：上传图片、上传视频、生成二维码都会按新配置落盘。
 */
@RequirePerm("system")
@RestController
@RequestMapping("/admin/storage-config")
@RequiredArgsConstructor
public class StorageConfigController {

    private final StorageConfigService storageConfigService;
    private final OssStorageStrategy ossStorageStrategy;

    @GetMapping
    public Result<SysStorageConfig> get() {
        return Result.success(storageConfigService.loadForAdmin());
    }

    @AuditLog(module = "存储配置", action = "保存")
    @PostMapping("/save")
    public Result<Void> save(@RequestBody SysStorageConfig config) {
        storageConfigService.save(config);
        return Result.success();
    }

    /** OSS 连通性测试：只允许当前配置类型为 oss 时执行 */
    @AuditLog(module = "存储配置", action = "OSS连通性测试")
    @PostMapping("/test")
    public Result<String> test() {
        ossStorageStrategy.testConnection();
        return Result.success("OSS 连接正常，测试文件已自动清理");
    }
}
