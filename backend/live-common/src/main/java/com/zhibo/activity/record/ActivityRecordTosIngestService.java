package com.zhibo.activity.record;

import com.zhibo.activity.ActivityEntity;
import com.zhibo.activity.ActivityRecordEntity;
import com.zhibo.activity.ActivityRecordRepository;
import com.zhibo.activity.ActivityRepository;
import com.zhibo.activity.VhallRoomIdResolver;
import com.zhibo.liveprovider.LiveProvider;
import com.zhibo.liveprovider.LiveProviderRegistry;
import com.zhibo.liveprovider.dto.ProviderRecordInfo;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * 回放成片：下载 →（zip 取最大 mp4）→ 服务端 TOS PutObject → 更新/插入 {@code activity_record} 为 {@code ready}。
 * 事件 6 与管理端列表补偿共用（admin / live 均可注入）。
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ActivityRecordTosIngestService {

    static final String PROVIDER_VHALL = "vhall";

    private static final DateTimeFormatter CREATED_AT = DateTimeFormatter
            .ofPattern("uuuu-MM-dd HH:mm:ss")
            .withResolverStyle(ResolverStyle.STRICT);

    /**
     * JVM 内存：正在下载/已投递 ingest 的微吼 {@code record_id} → 开始下载（投递）时间。
     * 进程重启即清空。投递 {@link #processDownloadAsync} 前须 {@link #tryMarkInFlight}，
     * 避免任务还在线程池排队时 {@link #isIngestInFlight} 为 false，列表误判卡死并重复投递。
     * 超时按本 map 的开始时间判断，<b>不</b>依赖库 {@code updated_time}。
     */
    private final ConcurrentHashMap<Long, Instant> ingestInFlight = new ConcurrentHashMap<>();

    private final ActivityRecordRepository activityRecordRepository;
    private final ActivityRepository activityRepository;
    private final VhallRoomIdResolver roomIdResolver;
    private final LiveProviderRegistry providerRegistry;
    private final ActivityRecordTosClient tosClient;
    private final ActivityRecordFileFetcher fileFetcher;

    /**
     * in-flight 僵尸阈值（分钟）：map 内开始时间过久 → 视为打断，清条目并按「不在 map」处理。
     * 配置：{@code zhibo.activity-record.downloading-stale-minutes}，默认 30。
     */
    @Value("${zhibo.activity-record.downloading-stale-minutes:30}")
    private int downloadingStaleMinutes = 30;

    /**
     * 本进程是否真在下该 record（在 map 且未超时）。
     * 超时条目会当场清除并返回 false——重启后 map 空也返回 false，列表/下载须立刻 re-check。
     */
    public boolean isIngestInFlight(long recordId) {
        return getIngestStartedAt(recordId) != null;
    }

    /**
     * 内存中该回放开始下载（投递）的时间；不在 map 或已超时则 {@code null}。
     * 供下载接口在 {@code packStatus=2} 时回给前端。
     */
    public Instant getIngestStartedAt(long recordId) {
        Instant started = ingestInFlight.get(recordId);
        if (started == null) {
            return null;
        }
        if (isStartStale(started)) {
            if (ingestInFlight.remove(recordId, started)) {
                log.info("回放 ingest in-flight 超时清僵尸 recordId={} started={}", recordId, started);
            }
            return null;
        }
        return started;
    }

    /**
     * 投递异步前占用 in-flight（写入开始时间）；已占用且未超时则返回 false。
     * 若原条目已超时，清僵尸后重新占用。
     */
    public boolean tryMarkInFlight(long recordId) {
        Instant now = Instant.now();
        Instant existing = ingestInFlight.putIfAbsent(recordId, now);
        if (existing == null) {
            return true;
        }
        if (!isStartStale(existing)) {
            return false;
        }
        if (ingestInFlight.replace(recordId, existing, now)) {
            log.info("回放 ingest in-flight 僵尸被替换 recordId={} oldStarted={}", recordId, existing);
            return true;
        }
        // 丢失竞态：可能已被清掉或被别人占用
        return ingestInFlight.putIfAbsent(recordId, Instant.now()) == null;
    }

    /**
     * 强制清 in-flight（线程池拒收后重投前 / 启动补偿等）。
     */
    public void clearInFlight(long recordId) {
        ingestInFlight.remove(recordId);
    }

    /** 单测：按指定开始时间写入（用于验证超时清僵尸）。 */
    boolean tryMarkInFlightAt(long recordId, Instant startedAt) {
        return ingestInFlight.putIfAbsent(recordId, startedAt) == null;
    }

    private boolean isStartStale(Instant started) {
        int minutes = downloadingStaleMinutes > 0 ? downloadingStaleMinutes : 30;
        return started.isBefore(Instant.now().minus(Duration.ofMinutes(minutes)));
    }

    @Async(ActivityRecordAsyncConfig.RECORD_INGEST_EXECUTOR)
    public void processFailAsync(long recordId) {
        markFailed(recordId);
    }

    @Async(ActivityRecordAsyncConfig.RECORD_INGEST_EXECUTOR)
    public void processDownloadAsync(long recordId, String downloadUrl) {
        // 回调等路径可能未预占；putIfAbsent 不覆盖已有开始时间。列表路径应已 tryMarkInFlight。
        ingestInFlight.putIfAbsent(recordId, Instant.now());
        try {
            ingestFromUrlUnlocked(recordId, downloadUrl);
        } catch (Throwable ex) {
            log.warn("回放 TOS 异步处理未捕获异常 recordId={}: {}", recordId, ex.toString());
            markFailed(recordId);
        } finally {
            ingestInFlight.remove(recordId);
        }
    }

    /** 事件 6 {@code status=3}：有本站行则标 failed。 */
    void markFailed(long recordId) {
        List<ActivityRecordEntity> rows = activityRecordRepository.findByRecordId(recordId);
        if (rows.isEmpty()) {
            log.info("回放打包失败回调无本站行，跳过 recordId={}", recordId);
            return;
        }
        for (ActivityRecordEntity row : rows) {
            if (Integer.valueOf(ActivityRecordEntity.STATUS_READY).equals(row.getStatus())
                    && hasText(row.getTosUrl())) {
                log.info("回放已 ready，忽略失败回调 activityId={} recordId={}",
                        row.getActivityId(), recordId);
                continue;
            }
            try {
                row.setStatus(ActivityRecordEntity.STATUS_FAILED);
                activityRecordRepository.save(row);
                log.info("回放标 failed activityId={} recordId={}", row.getActivityId(), recordId);
            } catch (RuntimeException ex) {
                log.error("回放标 failed 落库失败 activityId={} recordId={}: {}",
                        row.getActivityId(), recordId, ex.getMessage());
            }
        }
    }

    /**
     * 同步落库路径（单测直接调；生产由 {@link #processDownloadAsync} 调用）。
     * <p>调用方（列表补偿 / 下载接口）可能已先把行标成 {@code downloading}；此处用 in-flight 去重，
     * <b>不能</b>仅因 status=downloading 就跳过，否则永远不下 TOS。
     */
    void ingestFromUrl(long recordId, String downloadUrl) {
        if (!tryMarkInFlight(recordId)) {
            log.info("回放 ingest 已在进行，跳过重复任务 recordId={}", recordId);
            return;
        }
        try {
            ingestFromUrlUnlocked(recordId, downloadUrl);
        } finally {
            ingestInFlight.remove(recordId);
        }
    }

    /** 假定调用方已持有 in-flight（或单测路径由 {@link #ingestFromUrl} 包一层）。 */
    private void ingestFromUrlUnlocked(long recordId, String downloadUrl) {
        List<ActivityRecordEntity> rows = activityRecordRepository.findByRecordId(recordId);
        if (!rows.isEmpty()) {
            for (ActivityRecordEntity row : rows) {
                ingestExistingRow(row, downloadUrl);
            }
            return;
        }
        ingestMissingRow(recordId, downloadUrl);
    }

    private void ingestExistingRow(ActivityRecordEntity row, String downloadUrl) {
        if (Integer.valueOf(ActivityRecordEntity.STATUS_READY).equals(row.getStatus())
                && hasText(row.getTosUrl())) {
            log.info("回放已 ready，跳过上传 activityId={} recordId={}",
                    row.getActivityId(), row.getRecordId());
            return;
        }
        if (downloadUrl == null || downloadUrl.isBlank()) {
            log.warn("回放 ingest 无 downloadUrl，标 failed activityId={} recordId={}",
                    row.getActivityId(), row.getRecordId());
            persistFailed(row);
            return;
        }

        if (!Integer.valueOf(ActivityRecordEntity.STATUS_DOWNLOADING).equals(row.getStatus())) {
            row.setStatus(ActivityRecordEntity.STATUS_DOWNLOADING);
            activityRecordRepository.save(row);
        }

        Path workDir = null;
        try {
            log.info("回放开始下载 activityId={} recordId={}", row.getActivityId(), row.getRecordId());
            workDir = Files.createTempDirectory("activity-record-" + row.getRecordId() + "-");
            Path mp4 = fileFetcher.fetchMp4(downloadUrl.trim(), workDir);
            long size = Files.size(mp4);
            String objectKey = buildObjectKey(row.getProvider(), row.getActivityId(), row.getRecordId());
            log.info("回放开始上传 TOS activityId={} recordId={} bytes={} key={}",
                    row.getActivityId(), row.getRecordId(), size, objectKey);
            String tosUrl = tosClient.putFile(objectKey, mp4, "video/mp4");
            row.setObjectKey(objectKey);
            row.setTosUrl(tosUrl);
            row.setStatus(ActivityRecordEntity.STATUS_READY);
            activityRecordRepository.save(row);
            log.info("回放已上传 TOS ready activityId={} recordId={} key={}",
                    row.getActivityId(), row.getRecordId(), objectKey);
        } catch (Throwable ex) {
            if (ex instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            log.warn("回放下载/上传失败 activityId={} recordId={}: {}",
                    row.getActivityId(), row.getRecordId(), ex.toString());
            persistFailed(row);
        } finally {
            deleteRecursively(workDir);
        }
    }

    /**
     * 无本站行（事件 4 丢失 / 补偿只 submit）：先插 {@code downloading}，再拉成 TOS → {@code ready}/{@code failed}。
     */
    private void ingestMissingRow(long recordId, String downloadUrl) {
        LiveProvider liveProvider;
        try {
            liveProvider = providerRegistry.require(PROVIDER_VHALL);
        } catch (RuntimeException ex) {
            log.warn("回放补偿插行取 LiveProvider 失败 recordId={}: {}", recordId, ex.getMessage());
            return;
        }

        ProviderRecordInfo info;
        try {
            info = liveProvider.getRecordInfo(recordId);
        } catch (RuntimeException ex) {
            log.warn("回放补偿插行 get-info 失败 recordId={}: {}", recordId, ex.getMessage());
            return;
        }

        Long webinarId = info == null ? null : info.webinarId();
        if (webinarId == null || webinarId <= 0) {
            log.warn("回放补偿插行 get-info 无 webinar_id recordId={}", recordId);
            return;
        }

        Optional<String> roomId = roomIdResolver.resolveRoomId(String.valueOf(webinarId));
        if (roomId.isEmpty()) {
            log.warn("回放补偿插行找不到本站活动 webinar_id={} recordId={}", webinarId, recordId);
            return;
        }

        Optional<ActivityEntity> found = activityRepository.findById(roomId.get());
        if (found.isEmpty()) {
            log.warn("回放补偿插行活动不存在 id={} recordId={}", roomId.get(), recordId);
            return;
        }
        ActivityEntity activity = found.get();
        if (activity.getDeletedTime() != null) {
            log.info("回放补偿插行活动已软删 id={} recordId={}", activity.getId(), recordId);
            return;
        }
        String provider = activity.getProvider();
        if (provider == null || provider.isBlank()) {
            provider = PROVIDER_VHALL;
        }

        // 并发：事件 4 可能刚插行
        Optional<ActivityRecordEntity> raced = activityRecordRepository
                .findByActivityIdAndProviderAndRecordId(activity.getId(), provider, recordId);
        if (raced.isPresent()) {
            ingestExistingRow(raced.get(), downloadUrl);
            return;
        }

        ActivityRecordEntity entity = new ActivityRecordEntity();
        entity.setActivityId(activity.getId());
        entity.setProvider(provider);
        entity.setRecordId(recordId);
        entity.setTitle(info.title());
        entity.setDurationSec(info.durationSec());
        entity.setCoverUrl(info.coverUrl());
        entity.setStorageKb(info.storageKb());
        entity.setStatus(ActivityRecordEntity.STATUS_DOWNLOADING);
        entity.setProviderCreatedTime(parseCreatedAt(info.createdTime()));
        try {
            activityRecordRepository.save(entity);
        } catch (DataIntegrityViolationException ex) {
            log.info("回放补偿插 downloading 并发冲突，改更新已有行 activityId={} recordId={}",
                    activity.getId(), recordId);
            activityRecordRepository
                    .findByActivityIdAndProviderAndRecordId(activity.getId(), provider, recordId)
                    .ifPresent(row -> ingestExistingRow(row, downloadUrl));
            return;
        }

        Path workDir = null;
        try {
            log.info("回放补偿开始下载 activityId={} recordId={}", activity.getId(), recordId);
            workDir = Files.createTempDirectory("activity-record-" + recordId + "-");
            Path mp4 = fileFetcher.fetchMp4(downloadUrl, workDir);
            long size = Files.size(mp4);
            String objectKey = buildObjectKey(provider, activity.getId(), recordId);
            log.info("回放补偿开始上传 TOS activityId={} recordId={} bytes={} key={}",
                    activity.getId(), recordId, size, objectKey);
            String tosUrl = tosClient.putFile(objectKey, mp4, "video/mp4");
            entity.setObjectKey(objectKey);
            entity.setTosUrl(tosUrl);
            entity.setStatus(ActivityRecordEntity.STATUS_READY);
            activityRecordRepository.save(entity);
            log.info("回放补偿插入 downloading→ready activityId={} recordId={} key={}",
                    activity.getId(), recordId, objectKey);
        } catch (Throwable ex) {
            if (ex instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            log.warn("回放补偿下载/上传失败 activityId={} recordId={}: {}",
                    activity.getId(), recordId, ex.toString());
            persistFailed(entity);
        } finally {
            deleteRecursively(workDir);
        }
    }

    private void persistFailed(ActivityRecordEntity row) {
        try {
            row.setStatus(ActivityRecordEntity.STATUS_FAILED);
            activityRecordRepository.save(row);
        } catch (RuntimeException ex) {
            log.error("回放标 failed 落库失败 activityId={} recordId={}: {}",
                    row.getActivityId(), row.getRecordId(), ex.getMessage());
        }
    }

    public static String buildObjectKey(String provider, String activityId, long recordId) {
        return ActivityRecordTosClient.RECORD_PREFIX + provider + "_" + activityId + "_" + recordId + ".mp4";
    }

    private LocalDateTime parseCreatedAt(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return LocalDateTime.parse(raw.trim(), CREATED_AT);
        } catch (DateTimeParseException ex) {
            log.warn("回放 created_at 无法解析: {}", raw);
            return null;
        }
    }

    private static boolean hasText(String s) {
        return s != null && !s.isBlank();
    }

    private static void deleteRecursively(Path root) {
        if (root == null || !Files.exists(root)) {
            return;
        }
        try (var walk = Files.walk(root)) {
            walk.sorted(Comparator.reverseOrder()).forEach(p -> {
                try {
                    Files.deleteIfExists(p);
                } catch (IOException ignored) {
                    // best-effort
                }
            });
        } catch (IOException ignored) {
            // best-effort
        }
    }
}
