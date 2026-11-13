package com.zhibo.activity.session;

import com.zhibo.activity.ActivityEntity;
import com.zhibo.activity.ActivityLiveSessionEntity;
import com.zhibo.activity.ActivityLiveSessionRepository;
import com.zhibo.activity.ActivityRepository;
import com.zhibo.activity.AttendanceTimes;
import com.zhibo.liveprovider.LiveProvider;
import com.zhibo.liveprovider.LiveProviderRegistry;
import com.zhibo.liveprovider.dto.ProviderWebinarSwitchItem;
import com.zhibo.liveprovider.dto.ProviderWebinarSwitchList;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * D119：拉微吼 {@code get-webinar-switch-list} 按 {@code switch_id} upsert 场次，并回写汇总 {@code live_actual_*}。
 * 失败只打日志，不向外抛（summary / 结束回调 / 统计旁路同 D106 降级）。
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ActivityLiveSessionSyncService {

    private final ActivityLiveSessionRepository sessionRepository;
    private final ActivityRepository activityRepository;
    private final LiveProviderRegistry providerRegistry;

    /**
     * 对账旁路：吞异常。返回本库场次（同步后或失败时的原样）。
     */
    public List<ActivityLiveSessionEntity> syncQuietly(ActivityEntity activity) {
        try {
            return syncForActivity(activity);
        } catch (RuntimeException ex) {
            log.warn("场次 switch-list sync 失败 activityId={}: {}",
                    activity == null ? null : activity.getId(), ex.getMessage());
            if (activity == null || activity.getId() == null) {
                return List.of();
            }
            return sessionRepository.findByActivityId(activity.getId());
        }
    }

    /**
     * 按云列表 upsert；回写活动汇总列（最早 start / 最晚 end，末结覆盖）。
     * 同步成功后删除本站临时 {@code switch_id=0} 未闭合段（云列表为真相）。
     * {@code REQUIRES_NEW}：失败不污染结束回调 / summary 外层事务。
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public List<ActivityLiveSessionEntity> syncForActivity(ActivityEntity activity) {
        if (activity == null || activity.getId() == null) {
            return List.of();
        }
        String roomId = activity.getProviderRoomId();
        if (roomId == null || roomId.isBlank()) {
            return sessionRepository.findByActivityId(activity.getId());
        }

        LiveProvider liveProvider = providerRegistry.require(activity.getProvider());
        ProviderWebinarSwitchList cloud = liveProvider.listWebinarSwitches(roomId.trim());
        List<ProviderWebinarSwitchItem> items =
                cloud == null || cloud.switchList() == null ? List.of() : cloud.switchList();

        String provider = activity.getProvider() == null ? "vhall" : activity.getProvider().trim();
        for (ProviderWebinarSwitchItem item : items) {
            if (item == null || item.id() == null || item.id() <= 0) {
                continue;
            }
            upsertFromCloud(activity.getId(), provider, item);
        }

        // 云列表到齐后清掉回调临时段，避免与真实 switch_id 双计
        sessionRepository.findByActivityIdAndProviderAndSwitchId(
                        activity.getId(), provider, ActivityLiveSessionEntity.PROVISIONAL_SWITCH_ID)
                .ifPresent(provisional -> {
                    sessionRepository.delete(provisional);
                    log.info("场次 sync 删除临时段 activityId={} provisionalId={}",
                            activity.getId(), provisional.getId());
                });

        List<ActivityLiveSessionEntity> rows = sessionRepository.findByActivityId(activity.getId());
        rewriteActualTimes(activity, rows);
        return rows;
    }

    private void upsertFromCloud(String activityId, String provider, ProviderWebinarSwitchItem item) {
        ActivityLiveSessionEntity row = sessionRepository
                .findByActivityIdAndProviderAndSwitchId(activityId, provider, item.id())
                .orElseGet(ActivityLiveSessionEntity::new);
        boolean isNew = row.getId() == null;
        if (isNew) {
            row.setActivityId(activityId);
            row.setProvider(provider);
            row.setSwitchId(item.id());
        }
        LocalDateTime start = parseCloudTime(item.startTime());
        LocalDateTime end = parseCloudTime(item.endTime());
        if (start != null) {
            row.setStartTime(start);
        }
        // 云侧有 end 则写入/覆盖；无 end 保留本站已有（直播中）
        if (end != null) {
            row.setEndTime(end);
        }
        if (item.type() != null) {
            row.setSessionType(item.type());
        }
        if (item.startType() != null) {
            row.setStartType(item.startType());
        }
        if (item.endType() != null) {
            row.setEndType(item.endType());
        }
        try {
            sessionRepository.save(row);
        } catch (DataIntegrityViolationException ex) {
            // 并发插入：再读并更新
            ActivityLiveSessionEntity existing = sessionRepository
                    .findByActivityIdAndProviderAndSwitchId(activityId, provider, item.id())
                    .orElseThrow(() -> ex);
            if (start != null) {
                existing.setStartTime(start);
            }
            if (end != null) {
                existing.setEndTime(end);
            }
            if (item.type() != null) {
                existing.setSessionType(item.type());
            }
            if (item.startType() != null) {
                existing.setStartType(item.startType());
            }
            if (item.endType() != null) {
                existing.setEndType(item.endType());
            }
            sessionRepository.save(existing);
        }
    }

    /**
     * 回写汇总：{@code live_actual_start}=各段最早 start；{@code live_actual_end}=各段最晚 end（覆盖）。
     */
    public void rewriteActualTimes(ActivityEntity activity, List<ActivityLiveSessionEntity> rows) {
        if (activity == null) {
            return;
        }
        LocalDateTime minStart = null;
        LocalDateTime maxEnd = null;
        if (rows != null) {
            for (ActivityLiveSessionEntity row : rows) {
                if (row == null) {
                    continue;
                }
                if (row.getStartTime() != null
                        && (minStart == null || row.getStartTime().isBefore(minStart))) {
                    minStart = row.getStartTime();
                }
                if (row.getEndTime() != null
                        && (maxEnd == null || row.getEndTime().isAfter(maxEnd))) {
                    maxEnd = row.getEndTime();
                }
            }
        }
        boolean changed = false;
        if (minStart != null) {
            LocalDateTime cur = activity.getLiveActualStartTime();
            if (cur == null || minStart.isBefore(cur)) {
                activity.setLiveActualStartTime(minStart);
                changed = true;
            }
        }
        if (maxEnd != null) {
            LocalDateTime cur = activity.getLiveActualEndTime();
            if (cur == null || !maxEnd.equals(cur)) {
                activity.setLiveActualEndTime(maxEnd);
                changed = true;
            }
        }
        if (changed) {
            activityRepository.save(activity);
            log.info("场次 sync 回写 live_actual activityId={} start={} end={}",
                    activity.getId(), activity.getLiveActualStartTime(), activity.getLiveActualEndTime());
        }
    }

    static LocalDateTime parseCloudTime(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String t = raw.trim();
        try {
            return AttendanceTimes.parse(t, "t");
        } catch (IllegalArgumentException ignored) {
            // fall through
        }
        try {
            return LocalDateTime.parse(t.replace(' ', 'T'));
        } catch (Exception ignored) {
            return null;
        }
    }

    /** 测试辅助：构造可断言的列表副本。 */
    static List<ActivityLiveSessionEntity> copyOf(List<ActivityLiveSessionEntity> rows) {
        return rows == null ? List.of() : new ArrayList<>(rows);
    }
}
