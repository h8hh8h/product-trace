package com.trace.product.task;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.trace.product.entity.BatchCodeTask;
import com.trace.product.mapper.BatchCodeTaskMapper;
import com.trace.product.service.StorageConfigService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

/**
 * 定时清理任务
 * <p>
 * 解决三类磁盘/数据库膨胀问题：
 *   1. 一物一码导出的 CSV 文件长期堆积
 *   2. 产品图片/视频被替换或产品被删除后残留的孤儿文件
 *   3. scan_record 扫码明细表无限增长
 * <p>
 * 全部任务可通过 trace.cleanup.enabled=false 一键关闭。
 * 默认 cron 都放在凌晨低峰期执行。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ScheduledCleanupTask {

    private final BatchCodeTaskMapper batchCodeTaskMapper;
    private final StorageConfigService storageConfigService;
    private final JdbcTemplate jdbcTemplate;

    @Value("${trace.cleanup.enabled:true}")
    private boolean enabled;

    @Value("${trace.cleanup.csv.retain-days:30}")
    private int csvRetainDays;

    @Value("${trace.cleanup.orphan.retain-days:7}")
    private int orphanRetainDays;

    @Value("${trace.cleanup.scan-record.retain-days:0}")
    private int scanRecordRetainDays;

    // ============================================================
    // 1) 清理过期 CSV（每日 03:10）
    // ============================================================
    @Scheduled(cron = "${trace.cleanup.csv.cron:0 10 3 * * ?}")
    public void cleanExpiredCsv() {
        if (!enabled) {
            return;
        }
        LocalDateTime deadline = LocalDateTime.now().minusDays(csvRetainDays);
        List<BatchCodeTask> tasks = batchCodeTaskMapper.selectList(
                new LambdaUpdateWrapper<BatchCodeTask>()
                        .lt(BatchCodeTask::getCreateTime, deadline)
                        .isNotNull(BatchCodeTask::getCsvFilePath));
        int deleted = 0;
        for (BatchCodeTask task : tasks) {
            try {
                if (task.getCsvFilePath() != null && Files.deleteIfExists(Paths.get(task.getCsvFilePath()))) {
                    deleted++;
                }
                batchCodeTaskMapper.update(null, new LambdaUpdateWrapper<BatchCodeTask>()
                        .eq(BatchCodeTask::getId, task.getId())
                        .set(BatchCodeTask::getCsvFilePath, null)
                        .set(BatchCodeTask::getDownloadUrl, null));
            } catch (Exception e) {
                log.warn("清理 CSV 失败: taskId={}, path={}", task.getTaskId(), task.getCsvFilePath(), e);
            }
        }
        if (!tasks.isEmpty()) {
            log.info("CSV 清理完成：扫描 {} 条，实际删除 {} 个文件（保留 {} 天）",
                    tasks.size(), deleted, csvRetainDays);
        }
    }

    // ============================================================
    // 2) 清理孤儿媒体文件（每周日 03:20）
    // ============================================================
    @Scheduled(cron = "${trace.cleanup.orphan.cron:0 20 3 ? * SUN}")
    public void cleanOrphanMedia() {
        if (!enabled) {
            return;
        }
        Path root = Paths.get(storageConfigService.current().getLocalRootPath());
        if (!Files.isDirectory(root)) {
            log.warn("媒体根目录不存在，跳过孤儿文件清理: {}", root);
            return;
        }

        Set<String> referenced = collectReferencedPaths();
        log.info("孤儿文件清理开始：根目录={}, 数据库引用 {} 条, 保留 {} 天内的新文件",
                root, referenced.size(), orphanRetainDays);

        Instant deadline = Instant.now().minus(orphanRetainDays, ChronoUnit.DAYS);
        int scanned = 0;
        int deleted = 0;
        long freedBytes = 0L;

        try (Stream<Path> walk = Files.walk(root)) {
            List<Path> files = walk.filter(Files::isRegularFile).toList();
            for (Path file : files) {
                scanned++;
                String relative = root.relativize(file).toString().replace('\\', '/');
                if (referenced.contains(relative)) {
                    continue;
                }
                try {
                    FileTime lastModified = Files.getLastModifiedTime(file);
                    if (lastModified.toInstant().isAfter(deadline)) {
                        // 新文件可能是正在编辑中还未保存引用，先不动
                        continue;
                    }
                    long size = Files.size(file);
                    if (Files.deleteIfExists(file)) {
                        deleted++;
                        freedBytes += size;
                    }
                } catch (Exception e) {
                    log.warn("删除孤儿文件失败: {}", file, e);
                }
            }
        } catch (IOException e) {
            log.error("遍历媒体目录失败: {}", root, e);
            return;
        }

        log.info("孤儿文件清理完成：扫描 {} 个文件，删除 {} 个，释放 {} KB",
                scanned, deleted, freedBytes / 1024);
    }

    /**
     * 汇总数据库中被引用的媒体相对路径（如 images/2026/10/22/xxx.webp）
     */
    private Set<String> collectReferencedPaths() {
        Set<String> result = new HashSet<>();

        // 产品封面 / 视频 / 轮播图 JSON / 富文本 HTML，拼成一列便于一次扫描
        List<String> productBlobs = jdbcTemplate.queryForList(
                "SELECT CONCAT_WS(' ', IFNULL(cover_image,''), IFNULL(video_url,''), " +
                        "IFNULL(image_list,''), IFNULL(description,'')) AS blob " +
                        "FROM product WHERE deleted = 0", String.class);
        productBlobs.forEach(blob -> addIfPresent(result, blob));

        // 二维码图片
        List<String> qrUrls = jdbcTemplate.queryForList(
                "SELECT qr_image_url FROM product_code " +
                        "WHERE deleted = 0 AND qr_image_url IS NOT NULL AND qr_image_url <> ''", String.class);
        qrUrls.forEach(url -> addIfPresent(result, url));

        return result;
    }

    /**
     * 从任意 URL / JSON / HTML 片段中提取 upload 相对路径。
     * 宽松匹配，宁可漏删也不误删。
     */
    private void addIfPresent(Set<String> bucket, String text) {
        if (text == null || text.isBlank()) {
            return;
        }
        bucket.add(text);
        for (String token : text.split("[\\s\"'<>()\\[\\]{},;]")) {
            String t = token.trim();
            if (t.isEmpty()) {
                continue;
            }
            int idx = t.indexOf("/upload/");
            if (idx >= 0) {
                bucket.add(t.substring(idx + "/upload/".length()));
            }
            int mediaIdx = t.indexOf("/media/");
            if (mediaIdx >= 0) {
                bucket.add(t.substring(mediaIdx + "/media/".length()));
            }
        }
    }

    // ============================================================
    // 3) 归档/清理 scan_record 明细（每月 1 日 03:30，默认关闭）
    // ============================================================
    @Scheduled(cron = "${trace.cleanup.scan-record.cron:0 30 3 1 * ?}")
    public void archiveScanRecords() {
        if (!enabled || scanRecordRetainDays <= 0) {
            // retain-days <= 0 视为「保留全部」，不执行删除
            return;
        }
        LocalDateTime deadline = LocalDateTime.now().minusDays(scanRecordRetainDays);
        Integer deleted = jdbcTemplate.update(
                "DELETE FROM scan_record WHERE scan_time < ? LIMIT 50000", deadline);
        log.info("scan_record 清理完成：删除 {} 条 {} 之前的明细（保留 {} 天）",
                deleted, deadline, scanRecordRetainDays);
    }
}
