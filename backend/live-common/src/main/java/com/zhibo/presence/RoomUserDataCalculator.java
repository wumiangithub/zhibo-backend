package com.zhibo.presence;

import com.zhibo.activity.ActivityEntity;
import com.zhibo.activity.AttendanceTimes;
import com.zhibo.replay.RoomReplayWatchEntity;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 直播设置 · 用户数据：房间级观看人数/人次/时长 + 趋势默认区间。
 * 直播配对与区间规则与 {@link WatchDurationCalculator} 一致；只统计观众。
 */
public final class RoomUserDataCalculator {

    public record Result(RoomUserDataStats stats, long liveReplayUnionCount) {
    }

    private RoomUserDataCalculator() {
    }

    public static Result compute(
            ActivityEntity activity,
            List<PresenceEventEntity> events,
            List<RoomReplayWatchEntity> replays,
            LocalDateTime now) {
        LiveAgg live = liveAggregate(activity, events, now);
        ReplayAgg replay = replayAggregate(replays);

        Set<Long> positiveLive = new HashSet<>();
        for (Map.Entry<Long, Long> e : live.viewerSeconds.entrySet()) {
            if (e.getValue() > 0) {
                positiveLive.add(e.getKey());
            }
        }
        Set<Long> union = new HashSet<>(positiveLive);
        union.addAll(replay.viewerSeconds.keySet());

        LocalDateTime trendStart = trendStart(activity, now);
        LocalDateTime trendEnd = trendEnd(activity, now, trendStart);
        LocalDateTime[] clipped = clipToOneDay(trendStart, trendEnd);

        RoomUserDataStats stats = new RoomUserDataStats(
                positiveLive.size(),
                live.sessionCount,
                live.totalSec,
                replay.viewerSeconds.size(),
                replay.rowCount,
                replay.totalSec,
                AttendanceTimes.format(clipped[0]),
                AttendanceTimes.format(clipped[1]));
        return new Result(stats, union.size());
    }

    private static LiveAgg liveAggregate(
            ActivityEntity activity, List<PresenceEventEntity> events, LocalDateTime now) {
        LocalDateTime liveStart = liveStart(activity);
        LocalDateTime liveEndBound = liveEndBound(activity, now);
        boolean liveInProgress = activity != null && activity.getState() != null && activity.getState() == 1;
        boolean ended = isEnded(activity);
        boolean hasActualEnd = activity != null && activity.getLiveActualEndTime() != null;

        Map<Long, Map<String, LocalDateTime>> open = new LinkedHashMap<>();
        Map<Long, Long> seconds = new HashMap<>();
        long sessionCount = 0;

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
                    long sec = overlapSeconds(join, time, liveStart, liveEndBound);
                    add(seconds, viewerId, sec);
                    if (sec > 0) {
                        sessionCount++;
                    }
                }
            }
        }
        for (Map.Entry<Long, Map<String, LocalDateTime>> user : open.entrySet()) {
            for (LocalDateTime join : user.getValue().values()) {
                long sec = 0L;
                if (liveInProgress) {
                    sec = overlapSeconds(join, now, liveStart, now);
                } else if (hasActualEnd) {
                    sec = overlapSeconds(join, liveEndBound, liveStart, liveEndBound);
                } else if (ended) {
                    sec = 0L;
                } else if (liveEndBound != null) {
                    sec = overlapSeconds(join, liveEndBound, liveStart, liveEndBound);
                }
                add(seconds, user.getKey(), sec);
                if (sec > 0) {
                    sessionCount++;
                }
            }
        }
        long totalSec = 0L;
        for (long s : seconds.values()) {
            if (s > 0) {
                totalSec += s;
            }
        }
        return new LiveAgg(seconds, sessionCount, totalSec);
    }

    private static ReplayAgg replayAggregate(List<RoomReplayWatchEntity> replays) {
        Map<Long, Long> seconds = new HashMap<>();
        long rowCount = 0;
        long totalSec = 0;
        if (replays == null) {
            return new ReplayAgg(seconds, 0, 0);
        }
        for (RoomReplayWatchEntity row : replays) {
            if (row == null || row.getViewerId() == null || row.getStartTime() == null || row.getLastReportTime() == null) {
                continue;
            }
            long sec = Duration.between(row.getStartTime(), row.getLastReportTime()).getSeconds();
            if (sec < 0) {
                sec = 0;
            }
            rowCount++;
            totalSec += sec;
            seconds.merge(row.getViewerId(), sec, Long::sum);
        }
        return new ReplayAgg(seconds, rowCount, totalSec);
    }

    static LocalDateTime trendStart(ActivityEntity activity, LocalDateTime now) {
        if (activity != null && activity.getLiveActualStartTime() != null) {
            return activity.getLiveActualStartTime();
        }
        LocalDateTime planned = parseNullable(activity == null ? null : activity.getStartTime());
        if (planned != null) {
            return planned;
        }
        return now.minusHours(1);
    }

    static LocalDateTime trendEnd(ActivityEntity activity, LocalDateTime now, LocalDateTime start) {
        if (activity != null && activity.getLiveActualEndTime() != null) {
            return activity.getLiveActualEndTime();
        }
        if (activity != null && activity.getState() != null && activity.getState() == 1) {
            return now;
        }
        LocalDateTime plannedEnd = parseNullable(activity == null ? null : activity.getEndTime());
        if (plannedEnd != null) {
            return plannedEnd;
        }
        LocalDateTime end = now;
        if (!end.isAfter(start)) {
            end = start.plusMinutes(1);
        }
        return end;
    }

    static LocalDateTime[] clipToOneDay(LocalDateTime start, LocalDateTime end) {
        LocalDateTime s = start;
        LocalDateTime e = end;
        if (e.isBefore(s)) {
            e = s.plusMinutes(1);
        }
        if (Duration.between(s, e).compareTo(Duration.ofDays(1)) > 0) {
            e = s.plusDays(1);
        }
        return new LocalDateTime[] {s, e};
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

    private record LiveAgg(Map<Long, Long> viewerSeconds, long sessionCount, long totalSec) {
    }

    private record ReplayAgg(Map<Long, Long> viewerSeconds, long rowCount, long totalSec) {
    }
}
