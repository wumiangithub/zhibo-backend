package com.zhibo.presence;

import com.zhibo.activity.ActivityEntity;
import com.zhibo.activity.AttendanceTimes;
import com.zhibo.replay.RoomReplayWatchEntity;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 用户管理三时长：直播观看按 socket 配对并与实际直播区间重叠；回放按心跳墙钟。
 */
public final class WatchDurationCalculator {

    public record Durations(long liveSec, long replaySec) {
        public long totalSec() {
            return liveSec + replaySec;
        }
    }

    private WatchDurationCalculator() {
    }

    public static Map<Long, Durations> byViewer(
            ActivityEntity activity,
            List<PresenceEventEntity> events,
            List<RoomReplayWatchEntity> replays,
            LocalDateTime now) {
        Map<Long, Long> live = liveSeconds(activity, events, now);
        Map<Long, Long> replay = replaySeconds(replays);
        Map<Long, Durations> out = new HashMap<>();
        for (Long viewerId : live.keySet()) {
            out.put(viewerId, new Durations(live.getOrDefault(viewerId, 0L), replay.getOrDefault(viewerId, 0L)));
        }
        for (Map.Entry<Long, Long> e : replay.entrySet()) {
            out.putIfAbsent(e.getKey(), new Durations(0L, e.getValue()));
        }
        return out;
    }

    public static Durations zero() {
        return new Durations(0L, 0L);
    }

    static Map<Long, Long> liveSeconds(
            ActivityEntity activity, List<PresenceEventEntity> events, LocalDateTime now) {
        LocalDateTime liveStart = liveStart(activity);
        LocalDateTime liveEndBound = liveEndBound(activity, now);
        boolean liveInProgress = activity != null && activity.getState() != null && activity.getState() == 1;
        boolean ended = isEnded(activity);
        boolean hasActualEnd = activity != null && activity.getLiveActualEndTime() != null;

        Map<Long, Map<String, LocalDateTime>> open = new LinkedHashMap<>();
        Map<Long, Long> seconds = new HashMap<>();
        for (PresenceEventEntity event : ordered(events)) {
            Long viewerId = PresenceUserIds.viewerId(event.getUserId());
            String socketId = blankToNull(event.getSocketId());
            LocalDateTime time = event.getEventTime();
            if (viewerId == null || socketId == null || time == null) {
                continue;
            }
            Map<String, LocalDateTime> sockets = open.computeIfAbsent(viewerId, k -> new LinkedHashMap<>());
            if (PresenceEventEntity.TYPE_JOIN.equals(event.getType())) {
                sockets.putIfAbsent(socketId, time);
            } else if (PresenceEventEntity.TYPE_LEAVE.equals(event.getType())) {
                LocalDateTime join = sockets.remove(socketId);
                if (join != null) {
                    add(seconds, viewerId, overlapSeconds(join, time, liveStart, liveEndBound));
                }
            }
        }
        for (Map.Entry<Long, Map<String, LocalDateTime>> user : open.entrySet()) {
            for (LocalDateTime join : user.getValue().values()) {
                if (liveInProgress) {
                    add(seconds, user.getKey(), overlapSeconds(join, now, liveStart, now));
                } else if (hasActualEnd) {
                    add(seconds, user.getKey(), overlapSeconds(join, liveEndBound, liveStart, liveEndBound));
                } else if (ended) {
                    // 已结束仍无 Leave、也无实际结束时间：这段计 0
                } else if (liveEndBound != null) {
                    add(seconds, user.getKey(), overlapSeconds(join, liveEndBound, liveStart, liveEndBound));
                }
            }
        }
        return seconds;
    }

    static Map<Long, Long> replaySeconds(List<RoomReplayWatchEntity> replays) {
        Map<Long, Long> seconds = new HashMap<>();
        if (replays == null) {
            return seconds;
        }
        for (RoomReplayWatchEntity row : replays) {
            if (row == null || row.getViewerId() == null || row.getStartTime() == null || row.getLastReportTime() == null) {
                continue;
            }
            long sec = Duration.between(row.getStartTime(), row.getLastReportTime()).getSeconds();
            if (sec < 0) {
                sec = 0;
            }
            add(seconds, row.getViewerId(), sec);
        }
        return seconds;
    }

    private static LocalDateTime liveStart(ActivityEntity activity) {
        if (activity == null) {
            return null;
        }
        if (activity.getLiveActualStartTime() != null) {
            return activity.getLiveActualStartTime();
        }
        return parseNullable(activity.getStartTime());
    }

    private static LocalDateTime liveEndBound(ActivityEntity activity, LocalDateTime now) {
        if (activity == null) {
            return now;
        }
        if (activity.getLiveActualEndTime() != null) {
            return activity.getLiveActualEndTime();
        }
        if (activity.getState() != null && activity.getState() == 1) {
            return now;
        }
        return parseNullable(activity.getEndTime());
    }

    private static boolean isEnded(ActivityEntity activity) {
        Integer state = activity == null ? null : activity.getState();
        return state != null && (state == 3 || state == 4 || state == 5);
    }

    private static long overlapSeconds(
            LocalDateTime join, LocalDateTime leave, LocalDateTime windowStart, LocalDateTime windowEnd) {
        LocalDateTime start = join;
        LocalDateTime end = leave;
        if (windowStart != null && start.isBefore(windowStart)) {
            start = windowStart;
        }
        if (windowEnd != null && end.isAfter(windowEnd)) {
            end = windowEnd;
        }
        if (!end.isAfter(start)) {
            return 0L;
        }
        return Duration.between(start, end).getSeconds();
    }

    private static void add(Map<Long, Long> seconds, long viewerId, long delta) {
        if (delta <= 0) {
            seconds.putIfAbsent(viewerId, 0L);
            return;
        }
        seconds.merge(viewerId, delta, Long::sum);
    }

    private static LocalDateTime parseNullable(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return AttendanceTimes.parse(raw, "t");
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private static List<PresenceEventEntity> ordered(List<PresenceEventEntity> events) {
        if (events == null || events.isEmpty()) {
            return List.of();
        }
        List<PresenceEventEntity> ordered = new ArrayList<>(events);
        ordered.sort(Comparator
                .comparing(PresenceEventEntity::getEventTime, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(PresenceEventEntity::getId, Comparator.nullsLast(Comparator.naturalOrder())));
        return ordered;
    }

    private static String blankToNull(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        return raw.trim();
    }
}
