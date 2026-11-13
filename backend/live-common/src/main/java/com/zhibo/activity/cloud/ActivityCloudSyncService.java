package com.zhibo.activity.cloud;

import com.zhibo.activity.ActivityEntity;
import com.zhibo.activity.ActivityLiveSessionEntity;
import com.zhibo.activity.ActivityLiveSessionRepository;
import com.zhibo.activity.ActivityRepository;
import com.zhibo.activity.record.ActivityRecordListSyncService;
import com.zhibo.activity.session.ActivityLiveSessionSyncService;
import com.zhibo.liveprovider.LiveProvider;
import com.zhibo.liveprovider.LiveProviderRegistry;
import com.zhibo.liveprovider.dto.ProviderRoomSnapshot;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

/**
 * D120：微吼主动拉取统一补偿门面。回调仍是主路径；本类只做按活动补偿，失败只日志不挡主流程。
 * 复用 {@link ActivityLiveSessionSyncService} / {@link ActivityRecordListSyncService}，不复制落库逻辑。
 * <p>读路径旁路请用 {@link #syncQuietlyAsync}（不阻塞接口）；显式 cloud-sync 仍用 {@link #syncQuietly}。
 * <p>不含微吼 {@code webinar/get-list}：本站活动列表以本库为准，不用云列表替换。
 */
@Service
@Slf4j
public class ActivityCloudSyncService {

    private static final String PROVIDER_VHALL = "vhall";
    private static final int STATE_ENDED = 3;
    private static final int STATE_VOD = 4;
    private static final int STATE_REPLAY = 5;

    /** 同一 activityId：在飞或刚入队后若干毫秒内不再重复入队（防列表翻页狂打）。 */
    static final long DEDUP_WINDOW_MS = 5_000L;

    private final ActivityRepository activityRepository;
    private final ActivityLiveSessionRepository sessionRepository;
    private final LiveProviderRegistry providerRegistry;
    private final ActivityLiveSessionSyncService sessionSyncService;
    private final ActivityRecordListSyncService recordListSyncService;
    private final Executor cloudSyncExecutor;

    /** activityId → 最近一次异步入队时刻（epoch ms）。 */
    private final ConcurrentHashMap<String, Long> lastEnqueueAt = new ConcurrentHashMap<>();
    /** 正在执行的 activityId。 */
    private final ConcurrentHashMap<String, Boolean> inFlight = new ConcurrentHashMap<>();

    public ActivityCloudSyncService(
            ActivityRepository activityRepository,
            ActivityLiveSessionRepository sessionRepository,
            LiveProviderRegistry providerRegistry,
            ActivityLiveSessionSyncService sessionSyncService,
            ActivityRecordListSyncService recordListSyncService,
            @Qualifier(ActivityCloudSyncAsyncConfig.CLOUD_SYNC_EXECUTOR) Executor cloudSyncExecutor) {
        this.activityRepository = activityRepository;
        this.sessionRepository = sessionRepository;
        this.providerRegistry = providerRegistry;
        this.sessionSyncService = sessionSyncService;
        this.recordListSyncService = recordListSyncService;
        this.cloudSyncExecutor = cloudSyncExecutor;
    }

    /**
     * 能力开关。预设见 {@link #watchSummary()} / {@link #consoleSummary()} /
     * {@link #sessionsOnly()} / {@link #explicitFull()}。
     *
     * @param syncState          是否拉 {@code webinar/info} 对账 {@code state}
     * @param syncSessions       是否拉 {@code get-webinar-switch-list} upsert 场次
     * @param syncRecords        是否强制跑回放 get-list 补偿（忽略结束态门槛）
     * @param autoRecordsOnEnded 未强制 records 时：云侧或本库为结束类（3/4/5）才跑回放补偿
     */
    public record Options(
            boolean syncState,
            boolean syncSessions,
            boolean syncRecords,
            boolean autoRecordsOnEnded) {

        /** 观看 summary：info + 场次 + 结束类才回放（D106）。 */
        public static Options watchSummary() {
            return new Options(true, true, false, true);
        }

        /** 中控台 summary：info + 场次；不做回放补偿。 */
        public static Options consoleSummary() {
            return new Options(true, true, false, false);
        }

        /** 活动列表 / 用户数据 / recent-lives / live-state 结束后：仅场次。 */
        public static Options sessionsOnly() {
            return new Options(false, true, false, false);
        }

        /**
         * 管理端显式「同步云数据」：info + 场次；结束类（含本库已有 actual_end）才回放。
         */
        public static Options explicitFull() {
            return new Options(true, true, false, true);
        }
    }

    /**
     * 一次补偿结果（失败不抛；字段供调用方取场次或写显式接口响应）。
     */
    public record Result(
            boolean applicable,
            boolean stateAttempted,
            boolean stateUpdated,
            Integer state,
            boolean sessionsAttempted,
            List<ActivityLiveSessionEntity> sessions,
            boolean recordsAttempted,
            boolean recordsOk) {

        static Result skipped(ActivityEntity activity, ActivityLiveSessionRepository sessions) {
            List<ActivityLiveSessionEntity> rows = activity == null || activity.getId() == null
                    ? List.of()
                    : sessions.findByActivityId(activity.getId());
            Integer state = activity == null ? null : activity.getState();
            return new Result(false, false, false, state, false, rows, false, false);
        }
    }

    /**
     * 旁路异步补偿：立刻返回，后台拉微吼；异常只日志。
     * 同一 {@code activityId} 在飞或 {@link #DEDUP_WINDOW_MS} 内已入队则跳过。
     * 异步线程按 id 重新加载实体，避免跨线程脏实体。
     */
    public void syncQuietlyAsync(ActivityEntity activity, Options options) {
        if (activity == null || options == null) {
            return;
        }
        String activityId = activity.getId();
        if (activityId == null || activityId.isBlank()) {
            return;
        }
        if (!isVhallWithRoom(activity)) {
            return;
        }

        long now = System.currentTimeMillis();
        Long last = lastEnqueueAt.get(activityId);
        if (last != null && now - last < DEDUP_WINDOW_MS) {
            return;
        }
        if (inFlight.putIfAbsent(activityId, Boolean.TRUE) != null) {
            return;
        }
        lastEnqueueAt.put(activityId, now);

        Options opts = options;
        try {
            cloudSyncExecutor.execute(() -> {
                try {
                    syncQuietlyById(activityId, opts);
                } catch (RuntimeException ex) {
                    log.warn("云补偿异步失败 activityId={}: {}", activityId, ex.getMessage());
                } finally {
                    inFlight.remove(activityId);
                }
            });
        } catch (RuntimeException ex) {
            inFlight.remove(activityId);
            log.warn("云补偿异步入队失败 activityId={}: {}", activityId, ex.getMessage());
        }
    }

    /**
     * 按活动实体补偿；非 vhall / 无 roomId 跳过；任一步失败只打日志。
     * 读路径请用 {@link #syncQuietlyAsync}；本方法供显式 cloud-sync 与异步 worker。
     */
    public Result syncQuietly(ActivityEntity activity, Options options) {
        if (activity == null || options == null) {
            return Result.skipped(activity, sessionRepository);
        }
        if (!isVhallWithRoom(activity)) {
            return Result.skipped(activity, sessionRepository);
        }

        boolean stateAttempted = false;
        boolean stateUpdated = false;
        Integer cloudState = null;

        if (options.syncState()) {
            stateAttempted = true;
            try {
                LiveProvider liveProvider = providerRegistry.require(activity.getProvider());
                ProviderRoomSnapshot snap = liveProvider.fetchRoom(activity.getProviderRoomId().trim());
                if (snap != null) {
                    cloudState = snap.state();
                }
                if (cloudState != null) {
                    Integer localState = activity.getState();
                    if (localState == null || !localState.equals(cloudState)) {
                        Integer old = localState;
                        activity.setState(cloudState);
                        activityRepository.save(activity);
                        stateUpdated = true;
                        log.info("云补偿 info 对账写回 state activityId={} roomId={} {}→{}",
                                activity.getId(), activity.getProviderRoomId(), old, cloudState);
                    }
                }
            } catch (RuntimeException ex) {
                log.warn("云补偿 webinar/info 失败 activityId={} roomId={}: {}",
                        activity.getId(), activity.getProviderRoomId(), ex.getMessage());
            }
        }

        boolean sessionsAttempted = false;
        List<ActivityLiveSessionEntity> sessions = List.of();
        if (options.syncSessions()) {
            sessionsAttempted = true;
            sessions = sessionSyncService.syncQuietly(activity);
        } else if (activity.getId() != null) {
            sessions = sessionRepository.findByActivityId(activity.getId());
        }

        boolean recordsAttempted = false;
        boolean recordsOk = false;
        boolean wantRecords = options.syncRecords()
                || (options.autoRecordsOnEnded() && shouldSyncRecords(activity, cloudState));
        if (wantRecords) {
            recordsAttempted = true;
            try {
                ActivityRecordListSyncService.SyncResult sync =
                        recordListSyncService.syncForActivity(activity);
                recordsOk = sync != null && sync.providerListOk();
            } catch (RuntimeException ex) {
                log.warn("云补偿回放 get-list 失败 activityId={}: {}",
                        activity.getId(), ex.getMessage());
            }
        }

        Integer effectiveState = activity.getState();
        return new Result(
                true,
                stateAttempted,
                stateUpdated,
                effectiveState,
                sessionsAttempted,
                sessions == null ? List.of() : sessions,
                recordsAttempted,
                recordsOk);
    }

    /**
     * 按本站活动 id 加载后补偿；活动不存在返回 {@code null}（显式接口可 400）。
     */
    public Result syncQuietlyById(String activityId, Options options) {
        if (activityId == null || activityId.isBlank()) {
            return null;
        }
        return activityRepository.findById(activityId.trim())
                .filter(a -> a.getDeletedTime() == null)
                .map(a -> syncQuietly(a, options))
                .orElse(null);
    }

    static boolean isEndedLikeState(Integer state) {
        return state != null
                && (state == STATE_ENDED || state == STATE_VOD || state == STATE_REPLAY);
    }

    /**
     * 结束类 state，或本库已有 {@code live_actual_end_time} 时才值得拉回放（对齐 D106，避免预告狂拉）。
     */
    static boolean shouldSyncRecords(ActivityEntity activity, Integer cloudState) {
        if (isEndedLikeState(cloudState) || isEndedLikeState(activity.getState())) {
            return true;
        }
        return activity.getLiveActualEndTime() != null;
    }

    private static boolean isVhallWithRoom(ActivityEntity activity) {
        String provider = activity.getProvider();
        if (provider == null || !PROVIDER_VHALL.equalsIgnoreCase(provider.trim())) {
            return false;
        }
        String roomId = activity.getProviderRoomId();
        return roomId != null && !roomId.isBlank();
    }
}
