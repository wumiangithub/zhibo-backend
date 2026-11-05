package com.zhibo.activity.record;

import com.zhibo.activity.ActivityEntity;
import com.zhibo.activity.ActivityRecordEntity;
import com.zhibo.activity.ActivityRecordRepository;
import com.zhibo.liveprovider.LiveProvider;
import com.zhibo.liveprovider.LiveProviderRegistry;
import com.zhibo.liveprovider.dto.ProviderRecordDownload;
import com.zhibo.liveprovider.dto.ProviderRecordItem;
import com.zhibo.liveprovider.dto.ProviderRecordList;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

/**
 * D88 回放列表 get-list diff 补偿（管理端列表与观看 summary 共用）。
 * 缺行插 {@code pending_package}；对本库 {@code 1/2/5} 与卡死 {@code downloading(3)} 再 check 推进 TOS。
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ActivityRecordListSyncService {

    private static final DateTimeFormatter CREATED_TIME =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final ActivityRecordRepository activityRecordRepository;
    private final LiveProviderRegistry providerRegistry;
    private final ActivityRecordTosIngestService activityRecordTosIngestService;

    /**
     * 同步结果：本库行（补偿/推进后）+ 云侧 get-list 条目（失败则为空列表）。
     */
    public record SyncResult(
            Map<Long, ActivityRecordEntity> localByRecordId,
            List<ProviderRecordItem> providerItems,
            boolean providerListOk) {
    }

    /**
     * 跑一轮与管理端 {@code GET .../records/{id}} 相同的 get-list diff 补偿。
     * get-list / check 失败只打日志，不向外抛（调用方仍可回本库）。
     */
    public SyncResult syncForActivity(ActivityEntity activity) {
        if (activity == null || activity.getId() == null) {
            return new SyncResult(Map.of(), List.of(), false);
        }
        List<ActivityRecordEntity> localRows = activityRecordRepository.findByActivityId(activity.getId());
        Map<Long, ActivityRecordEntity> localByRecordId = new LinkedHashMap<>();
        for (ActivityRecordEntity row : localRows) {
            if (row.getRecordId() != null && row.getRecordId() > 0) {
                localByRecordId.putIfAbsent(row.getRecordId(), row);
            }
        }

        String roomId = activity.getProviderRoomId();
        if (roomId == null || roomId.isBlank()) {
            return new SyncResult(localByRecordId, List.of(), false);
        }

        LiveProvider liveProvider;
        try {
            liveProvider = providerRegistry.require(activity.getProvider());
        } catch (RuntimeException ex) {
            log.warn("回放列表补偿无可用 provider activityId={} provider={}: {}",
                    activity.getId(), activity.getProvider(), ex.getMessage());
            return new SyncResult(localByRecordId, List.of(), false);
        }

        ProviderRecordList providerList;
        try {
            providerList = liveProvider.listRecords(roomId);
        } catch (RuntimeException ex) {
            log.warn("回放列表 get-list 失败，降级本库仍推进卡死行 activityId={}: {}",
                    activity.getId(), ex.getMessage());
            for (ActivityRecordEntity row : localByRecordId.values()) {
                advanceExistingRecordOnList(liveProvider, row);
            }
            return new SyncResult(localByRecordId, List.of(), false);
        }

        List<ProviderRecordItem> providerItems =
                providerList == null || providerList.list() == null
                        ? List.of()
                        : providerList.list();

        for (ProviderRecordItem item : providerItems) {
            if (item == null || item.id() == null || item.id() <= 0) {
                continue;
            }
            ActivityRecordEntity existing = localByRecordId.get(item.id());
            if (existing == null) {
                ActivityRecordEntity inserted = compensateMissingRecord(activity, item);
                if (inserted != null) {
                    localByRecordId.put(item.id(), inserted);
                }
            }
        }
        for (ActivityRecordEntity row : localByRecordId.values()) {
            advanceExistingRecordOnList(liveProvider, row);
        }
        return new SyncResult(localByRecordId, providerItems, true);
    }

    /**
     * 缺行只插 {@code pending_package}；check 由 {@link #advanceExistingRecordOnList} 统一做。
     */
    private ActivityRecordEntity compensateMissingRecord(
            ActivityEntity activity, ProviderRecordItem item) {
        long recordId = item.id();
        ActivityRecordEntity row = insertPendingPackageFromListItem(activity, item);
        if (row == null) {
            return activityRecordRepository
                    .findByActivityIdAndProviderAndRecordId(
                            activity.getId(), activity.getProvider(), recordId)
                    .orElse(null);
        }
        return row;
    }

    /**
     * 列表对已有行再 check：仅 status∈{1,2,5}，或卡死 downloading（无 tos 且不在 JVM in-flight）。
     */
    private void advanceExistingRecordOnList(LiveProvider liveProvider, ActivityRecordEntity row) {
        if (row == null || row.getRecordId() == null || row.getRecordId() <= 0) {
            return;
        }
        Integer st = row.getStatus();
        if (st == null) {
            return;
        }
        boolean needsAdvance =
                st == ActivityRecordEntity.STATUS_PENDING_PACKAGE
                        || st == ActivityRecordEntity.STATUS_PACKAGING
                        || st == ActivityRecordEntity.STATUS_FAILED
                        || isStuckDownloading(row);
        if (!needsAdvance) {
            return;
        }
        if (st == ActivityRecordEntity.STATUS_DOWNLOADING) {
            log.info("回放列表发现卡死 downloading（无 TOS 且无 in-flight），立刻 re-check recordId={}",
                    row.getRecordId());
        }
        applyCheckDownloadOnList(liveProvider, row, row.getRecordId(), "列表推进已有行");
    }

    /**
     * 卡死 downloading：status=3、无 tos_url，且不在 JVM ingest in-flight。
     */
    public boolean isStuckDownloading(ActivityRecordEntity row) {
        if (row == null
                || !Integer.valueOf(ActivityRecordEntity.STATUS_DOWNLOADING).equals(row.getStatus())) {
            return false;
        }
        if (row.getTosUrl() != null && !row.getTosUrl().isBlank()) {
            return false;
        }
        long recordId = row.getRecordId() == null ? 0L : row.getRecordId();
        return !activityRecordTosIngestService.isIngestInFlight(recordId);
    }

    /**
     * check-download 后按微吼 pack 状态推进本库行（列表侧显式分支）。
     */
    private void applyCheckDownloadOnList(
            LiveProvider liveProvider, ActivityRecordEntity row, long recordId, String logTag) {
        try {
            ProviderRecordDownload pack = liveProvider.checkRecordDownload(recordId);
            Integer status = pack == null ? null : pack.status();
            String downloadUrl = pack == null ? null : pack.downloadUrl();
            if (status != null && status == 0) {
                try {
                    liveProvider.submitRecordDownload(recordId);
                    markLocalPackaging(row);
                    log.info("{} pack=0 显式 submit → packaging recordId={}", logTag, recordId);
                } catch (RuntimeException submitEx) {
                    row.setStatus(ActivityRecordEntity.STATUS_FAILED);
                    activityRecordRepository.save(row);
                    log.warn("{} pack=0 submit 失败 → failed recordId={}: {}",
                            logTag, recordId, submitEx.getMessage());
                }
            } else if (status != null && status == 2) {
                markLocalPackaging(row);
                log.info("{} pack=2 → packaging（进行中，本次不下载）recordId={}", logTag, recordId);
            } else if (status != null && status == 1
                    && downloadUrl != null && !downloadUrl.isBlank()) {
                if (!activityRecordTosIngestService.tryMarkInFlight(recordId)) {
                    if (!Integer.valueOf(ActivityRecordEntity.STATUS_DOWNLOADING).equals(row.getStatus())) {
                        row.setStatus(ActivityRecordEntity.STATUS_DOWNLOADING);
                        activityRecordRepository.save(row);
                    }
                    log.info("{} pack=1 已有 ingest in-flight，跳过重复投递 recordId={}", logTag, recordId);
                } else {
                    row.setStatus(ActivityRecordEntity.STATUS_DOWNLOADING);
                    activityRecordRepository.save(row);
                    try {
                        activityRecordTosIngestService.processDownloadAsync(recordId, downloadUrl.trim());
                        log.info("{} pack=1 有 URL → downloading + 异步 ingest recordId={}", logTag, recordId);
                    } catch (RuntimeException submitEx) {
                        activityRecordTosIngestService.clearInFlight(recordId);
                        row.setStatus(ActivityRecordEntity.STATUS_FAILED);
                        activityRecordRepository.save(row);
                        log.warn("{} pack=1 投递 ingest 失败 → failed recordId={}: {}",
                                logTag, recordId, submitEx.getMessage());
                    }
                }
            } else if (status != null && status == 1) {
                markLocalPackaging(row);
                log.warn("{} pack=1 但无 download_url，标 packaging 不投递 ingest recordId={}",
                        logTag, recordId);
            } else if (status != null && status == 3) {
                try {
                    liveProvider.submitRecordDownload(recordId);
                    markLocalPackaging(row);
                    log.info("{} pack=3 再 submit → packaging recordId={}", logTag, recordId);
                } catch (RuntimeException submitEx) {
                    row.setStatus(ActivityRecordEntity.STATUS_FAILED);
                    activityRecordRepository.save(row);
                    log.warn("{} pack=3 submit 失败 → failed recordId={}: {}",
                            logTag, recordId, submitEx.getMessage());
                }
            } else {
                log.info("{} check packStatus={} 不改 status={} recordId={}",
                        logTag, status, row.getStatus(), recordId);
            }
        } catch (RuntimeException ex) {
            log.warn("{} check/submit 失败 recordId={}: {}", logTag, recordId, ex.getMessage());
            if (Integer.valueOf(ActivityRecordEntity.STATUS_DOWNLOADING).equals(row.getStatus())
                    && (row.getTosUrl() == null || row.getTosUrl().isBlank())) {
                activityRecordTosIngestService.clearInFlight(recordId);
                row.setStatus(ActivityRecordEntity.STATUS_FAILED);
                activityRecordRepository.save(row);
                log.warn("{} check 失败 → failed（防卡死 downloading）recordId={}", logTag, recordId);
            }
        }
    }

    private void markLocalPackaging(ActivityRecordEntity row) {
        if (!Integer.valueOf(ActivityRecordEntity.STATUS_PACKAGING).equals(row.getStatus())) {
            row.setStatus(ActivityRecordEntity.STATUS_PACKAGING);
            activityRecordRepository.save(row);
        }
    }

    private ActivityRecordEntity insertPendingPackageFromListItem(
            ActivityEntity activity, ProviderRecordItem item) {
        ActivityRecordEntity entity = new ActivityRecordEntity();
        entity.setActivityId(activity.getId());
        entity.setProvider(activity.getProvider());
        entity.setRecordId(item.id());
        entity.setTitle(item.title());
        entity.setDurationSec(parseDurationToSec(item.duration()));
        entity.setCoverUrl(item.coverUrl());
        entity.setStorageKb(parseStorageKb(item.storage()));
        entity.setStatus(ActivityRecordEntity.STATUS_PENDING_PACKAGE);
        entity.setProviderCreatedTime(parseProviderCreatedTime(item.createdTime()));
        try {
            return activityRecordRepository.save(entity);
        } catch (DataIntegrityViolationException ex) {
            log.info("回放列表补偿并发插行 activityId={} recordId={}",
                    activity.getId(), item.id());
            return null;
        }
    }

    public static Integer parseDurationToSec(String duration) {
        if (duration == null || duration.isBlank()) {
            return null;
        }
        String[] parts = duration.trim().split(":");
        if (parts.length != 3) {
            return null;
        }
        try {
            int h = Integer.parseInt(parts[0]);
            int m = Integer.parseInt(parts[1]);
            int s = Integer.parseInt(parts[2]);
            if (h < 0 || m < 0 || m > 59 || s < 0 || s > 59) {
                return null;
            }
            return h * 3600 + m * 60 + s;
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    public static Long parseStorageKb(String storage) {
        if (storage == null || storage.isBlank()) {
            return null;
        }
        try {
            return Long.parseLong(storage.trim());
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    public static LocalDateTime parseProviderCreatedTime(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return LocalDateTime.parse(raw.trim(), CREATED_TIME);
        } catch (DateTimeParseException ex) {
            return null;
        }
    }
}
