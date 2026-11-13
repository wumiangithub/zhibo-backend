package com.zhibo.admin.dashboard;

import com.zhibo.activity.ActivityEntity;
import com.zhibo.activity.ActivityLiveSessionEntity;
import com.zhibo.activity.ActivityLiveSessionRepository;
import com.zhibo.activity.ActivityRepository;
import com.zhibo.activity.AttendanceTimes;
import com.zhibo.activity.cloud.ActivityCloudSyncService;
import com.zhibo.admin.dashboard.dto.RecentLiveRowResponse;
import com.zhibo.admin.dashboard.dto.RecentLivesResponse;
import com.zhibo.presence.LiveSessionWindows;
import com.zhibo.presence.PresenceEventEntity;
import com.zhibo.presence.PresenceEventRepository;
import com.zhibo.presence.PresenceUserIds;
import com.zhibo.presence.RoomUserDataCalculator;
import com.zhibo.presence.RoomUserDataStats;
import com.zhibo.replay.RoomReplayWatchEntity;
import com.zhibo.replay.RoomReplayWatchRepository;
import com.zhibo.viewer.ViewerEntity;
import com.zhibo.viewer.ViewerRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 工作台「最近直播数据」：本站 activity / presence / replay / viewer。
 * 本页活动异步旁路 sessionsOnly（不阻塞）；本响应读本库批量算指标。
 * 候选集：近 3 个自然日（Asia/Shanghai）场次数 ≥ pageSize 则只用近 3 天；否则用全局最近开播。
 */
@Service
public class DashboardRecentLivesService {

    static final ZoneId SHANGHAI = ZoneId.of("Asia/Shanghai");

    /** 近 3 个自然日：今天往前共 3 天（含今天）。 */
    static final int RECENT_NATURAL_DAYS = 3;

    static final int DEFAULT_PAGE = 1;
    static final int DEFAULT_PAGE_SIZE = 10;
    static final int MAX_PAGE_SIZE = 50;

    private final ActivityRepository activityRepository;
    private final ActivityLiveSessionRepository liveSessionRepository;
    private final PresenceEventRepository presenceEventRepository;
    private final RoomReplayWatchRepository replayWatchRepository;
    private final ViewerRepository viewerRepository;
    private final ActivityCloudSyncService activityCloudSyncService;
    private final Clock clock;

    public DashboardRecentLivesService(
            ActivityRepository activityRepository,
            ActivityLiveSessionRepository liveSessionRepository,
            PresenceEventRepository presenceEventRepository,
            RoomReplayWatchRepository replayWatchRepository,
            ViewerRepository viewerRepository,
            ActivityCloudSyncService activityCloudSyncService,
            Clock clock) {
        this.activityRepository = activityRepository;
        this.liveSessionRepository = liveSessionRepository;
        this.presenceEventRepository = presenceEventRepository;
        this.replayWatchRepository = replayWatchRepository;
        this.viewerRepository = viewerRepository;
        this.activityCloudSyncService = activityCloudSyncService;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public RecentLivesResponse recentLives(Integer pageRaw, Integer pageSizeRaw) {
        int page = normalizePage(pageRaw);
        int pageSize = normalizePageSize(pageSizeRaw);
        LocalDateTime now = LocalDateTime.ofInstant(clock.instant(), SHANGHAI);
        LocalDate today = now.toLocalDate();
        // [today-(N-1) 00:00, tomorrow 00:00)
        LocalDateTime windowStart = today.minusDays(RECENT_NATURAL_DAYS - 1L).atStartOfDay();
        LocalDateTime windowEnd = today.plusDays(1).atStartOfDay();

        long recentDaysCount =
                activityRepository.countWithActualStartBetween(windowStart, windowEnd);
        PageRequest pageable = PageRequest.of(page - 1, pageSize);
        Page<ActivityEntity> result;
        if (recentDaysCount >= pageSize) {
            result = activityRepository.findRecentWithActualStartBetween(
                    windowStart, windowEnd, pageable);
        } else {
            result = activityRepository.findRecentWithActualStart(pageable);
        }

        List<ActivityEntity> activities = result.getContent();
        // 本页异步旁路场次；不 await，指标仍按本库现有行
        for (ActivityEntity activity : activities) {
            activityCloudSyncService.syncQuietlyAsync(
                    activity, ActivityCloudSyncService.Options.sessionsOnly());
        }
        List<String> roomIds = activities.stream().map(ActivityEntity::getId).toList();

        Map<String, List<ActivityLiveSessionEntity>> sessionsByRoom = groupSessions(roomIds);
        Map<String, List<PresenceEventEntity>> eventsByRoom = groupEvents(roomIds);
        Map<String, List<RoomReplayWatchEntity>> replaysByRoom = groupReplays(roomIds);
        Map<String, LocalDateTime> viewerCreatedByUsername =
                loadViewerCreatedTimes(eventsByRoom);

        RecentLivesResponse response = new RecentLivesResponse();
        response.setPage(page);
        response.setPageSize(pageSize);
        response.setTotal(result.getTotalElements());
        for (ActivityEntity activity : activities) {
            String roomId = activity.getId();
            response.getList().add(buildRow(
                    activity,
                    sessionsByRoom.getOrDefault(roomId, List.of()),
                    eventsByRoom.getOrDefault(roomId, List.of()),
                    replaysByRoom.getOrDefault(roomId, List.of()),
                    viewerCreatedByUsername,
                    now));
        }
        return response;
    }

    private Map<String, List<ActivityLiveSessionEntity>> groupSessions(List<String> roomIds) {
        Map<String, List<ActivityLiveSessionEntity>> map = new HashMap<>();
        if (roomIds.isEmpty()) {
            return map;
        }
        for (ActivityLiveSessionEntity row : liveSessionRepository.findByActivityIdIn(roomIds)) {
            if (row == null || row.getActivityId() == null) {
                continue;
            }
            map.computeIfAbsent(row.getActivityId(), k -> new ArrayList<>()).add(row);
        }
        return map;
    }

    private Map<String, List<PresenceEventEntity>> groupEvents(List<String> roomIds) {
        Map<String, List<PresenceEventEntity>> map = new HashMap<>();
        if (roomIds.isEmpty()) {
            return map;
        }
        for (PresenceEventEntity row : presenceEventRepository.findByRoomIdIn(roomIds)) {
            if (row == null || row.getRoomId() == null) {
                continue;
            }
            map.computeIfAbsent(row.getRoomId(), k -> new ArrayList<>()).add(row);
        }
        return map;
    }

    private Map<String, List<RoomReplayWatchEntity>> groupReplays(List<String> roomIds) {
        Map<String, List<RoomReplayWatchEntity>> map = new HashMap<>();
        if (roomIds.isEmpty()) {
            return map;
        }
        for (RoomReplayWatchEntity row : replayWatchRepository.findByRoomIdIn(roomIds)) {
            if (row == null || row.getRoomId() == null) {
                continue;
            }
            map.computeIfAbsent(row.getRoomId(), k -> new ArrayList<>()).add(row);
        }
        return map;
    }

    /**
     * 本页所有 presence 观众 username 一次查出 created_time，供 newUserCount 复用。
     */
    private Map<String, LocalDateTime> loadViewerCreatedTimes(
            Map<String, List<PresenceEventEntity>> eventsByRoom) {
        Set<String> usernames = new HashSet<>();
        for (List<PresenceEventEntity> events : eventsByRoom.values()) {
            if (events == null) {
                continue;
            }
            for (PresenceEventEntity event : events) {
                if (event == null) {
                    continue;
                }
                String username = PresenceUserIds.viewerUsername(event.getUsername());
                if (username != null) {
                    usernames.add(username);
                }
            }
        }
        Map<String, LocalDateTime> created = new HashMap<>();
        if (usernames.isEmpty()) {
            return created;
        }
        for (ViewerEntity viewer : viewerRepository.findByUsernameIn(usernames)) {
            if (viewer == null || viewer.getUsername() == null) {
                continue;
            }
            created.put(viewer.getUsername(), viewer.getCreatedTime());
        }
        return created;
    }

    private RecentLiveRowResponse buildRow(
            ActivityEntity activity,
            List<ActivityLiveSessionEntity> sessions,
            List<PresenceEventEntity> events,
            List<RoomReplayWatchEntity> replays,
            Map<String, LocalDateTime> viewerCreatedByUsername,
            LocalDateTime now) {
        RoomUserDataCalculator.Result result = RoomUserDataCalculator.compute(
                activity, events, replays, sessions, now);
        RoomUserDataStats stats = result.stats();
        int peak = RoomUserDataCalculator.maxOnlinePeak(activity, events, sessions, now);

        LocalDateTime actualStart = LiveSessionWindows.trendStart(activity, sessions, now);
        LocalDateTime actualEnd = resolveDisplayEnd(activity, sessions, now);

        RecentLiveRowResponse row = new RecentLiveRowResponse();
        row.setId(activity.getId());
        row.setTitle(activity.getTitle());
        row.setCoverUrl(blankToEmpty(activity.getCoverUrl()));
        row.setStartTime(actualStart == null ? "" : AttendanceTimes.format(actualStart));
        row.setEndTime(actualEnd == null ? "" : AttendanceTimes.format(actualEnd));
        row.setState(activity.getState() == null ? 0 : activity.getState());
        row.setRoomViewCount(stats.liveViewCount());
        row.setRoomVisitorCount(stats.liveViewerCount());
        row.setWatchViewCount(stats.combinedViewCount());
        row.setWatchViewerCount(result.liveReplayUnionCount());
        row.setAvgWatchDurationSec(stats.liveAvgSec());
        row.setMaxOnlinePeak(peak);
        row.setNewUserCount(countNewUsersOnStartDay(actualStart, events, viewerCreatedByUsername));
        return row;
    }

    /**
     * 展示用末结：直播中且尚无汇总结束 → 空；否则各段最晚 end / live_actual_end。
     */
    private static LocalDateTime resolveDisplayEnd(
            ActivityEntity activity,
            List<ActivityLiveSessionEntity> sessions,
            LocalDateTime now) {
        boolean liveInProgress = activity.getState() != null && activity.getState() == 1;
        LocalDateTime maxEnd = null;
        if (sessions != null) {
            for (ActivityLiveSessionEntity row : sessions) {
                if (row == null) {
                    continue;
                }
                LocalDateTime end = row.getEndTime();
                if (end == null) {
                    if (liveInProgress) {
                        return null;
                    }
                    continue;
                }
                if (maxEnd == null || end.isAfter(maxEnd)) {
                    maxEnd = end;
                }
            }
        }
        if (maxEnd != null) {
            return maxEnd;
        }
        if (liveInProgress) {
            return null;
        }
        return activity.getLiveActualEndTime();
    }

    /**
     * 本场 presence 里出现过的观众 username ∩ created_time 落在开播自然日。
     */
    long countNewUsersOnStartDay(
            LocalDateTime actualStart,
            List<PresenceEventEntity> events,
            Map<String, LocalDateTime> viewerCreatedByUsername) {
        if (actualStart == null) {
            return 0L;
        }
        Set<String> viewers = new HashSet<>();
        if (events != null) {
            for (PresenceEventEntity event : events) {
                if (event == null) {
                    continue;
                }
                String username = PresenceUserIds.viewerUsername(event.getUsername());
                if (username != null) {
                    viewers.add(username);
                }
            }
        }
        if (viewers.isEmpty()) {
            return 0L;
        }
        // DB 时间列按 Asia/Shanghai 墙钟存，直接取日期
        LocalDate day = actualStart.toLocalDate();
        LocalDateTime dayStart = day.atStartOfDay();
        LocalDateTime dayEnd = day.plusDays(1).atStartOfDay();
        long count = 0L;
        for (String username : viewers) {
            LocalDateTime created = viewerCreatedByUsername.get(username);
            if (created != null
                    && !created.isBefore(dayStart)
                    && created.isBefore(dayEnd)) {
                count++;
            }
        }
        return count;
    }

    static int normalizePage(Integer pageRaw) {
        if (pageRaw == null) {
            return DEFAULT_PAGE;
        }
        if (pageRaw < 1) {
            throw new IllegalArgumentException("page 不能小于 1");
        }
        return pageRaw;
    }

    static int normalizePageSize(Integer pageSizeRaw) {
        if (pageSizeRaw == null) {
            return DEFAULT_PAGE_SIZE;
        }
        if (pageSizeRaw < 1 || pageSizeRaw > MAX_PAGE_SIZE) {
            throw new IllegalArgumentException("pageSize 需在 1~50");
        }
        return pageSizeRaw;
    }

    private static String blankToEmpty(String raw) {
        return raw == null || raw.isBlank() ? "" : raw.trim();
    }
}
