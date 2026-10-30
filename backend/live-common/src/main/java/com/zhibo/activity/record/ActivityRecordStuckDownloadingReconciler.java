package com.zhibo.activity.record;

import com.zhibo.activity.ActivityRecordEntity;
import com.zhibo.activity.ActivityRecordRepository;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * 启动后延迟扫一遍卡死 {@code downloading}（status=3 无 tos）：标 {@code failed}，
 * 清本进程残留 in-flight。真在下（JVM map 内未超时）跳过。用户再进列表或点下载可重试。
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ActivityRecordStuckDownloadingReconciler {

    private static final long STARTUP_DELAY_SECONDS = 15L;

    private final ActivityRecordRepository activityRecordRepository;
    private final ActivityRecordTosIngestService activityRecordTosIngestService;

    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "record-stuck-dl-reconcile");
            t.setDaemon(true);
            return t;
        });
        scheduler.schedule(() -> {
            try {
                reconcileStuckDownloading();
            } catch (Throwable ex) {
                log.warn("启动补偿卡死 downloading 异常: {}", ex.toString());
            } finally {
                scheduler.shutdown();
            }
        }, STARTUP_DELAY_SECONDS, TimeUnit.SECONDS);
        log.info("已安排 {}s 后扫描卡死 downloading（无 TOS）", STARTUP_DELAY_SECONDS);
    }

    void reconcileStuckDownloading() {
        List<ActivityRecordEntity> rows = activityRecordRepository.findByStatusWithoutTos(
                ActivityRecordEntity.STATUS_DOWNLOADING);
        if (rows.isEmpty()) {
            log.info("启动补偿：无 status=3 无 tos 行");
            return;
        }
        int marked = 0;
        for (ActivityRecordEntity row : rows) {
            if (row.getRecordId() == null || row.getRecordId() <= 0) {
                continue;
            }
            // 只信 JVM map（含开始时间超时自清）；不看库 updated_time。重启后 map 空 → 全标 failed。
            if (activityRecordTosIngestService.isIngestInFlight(row.getRecordId())) {
                continue;
            }
            activityRecordTosIngestService.clearInFlight(row.getRecordId());
            row.setStatus(ActivityRecordEntity.STATUS_FAILED);
            try {
                activityRecordRepository.save(row);
                marked++;
                log.info("启动补偿卡死 downloading → failed activityId={} recordId={}",
                        row.getActivityId(), row.getRecordId());
            } catch (RuntimeException ex) {
                log.warn("启动补偿标 failed 失败 activityId={} recordId={}: {}",
                        row.getActivityId(), row.getRecordId(), ex.getMessage());
            }
        }
        log.info("启动补偿卡死 downloading 完成：扫描 {} 行，标 failed {}", rows.size(), marked);
    }
}
