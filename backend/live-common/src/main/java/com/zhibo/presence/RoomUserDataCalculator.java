package com.zhibo.presence;

import com.zhibo.activity.ActivityEntity;
import com.zhibo.activity.ActivityLiveSessionEntity;
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
 * 直播配对与区间规则与 {@link WatchDurationCalculator} 一致（D119 多场次并集）；只统计观众。
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
        return compute(activity, events, replays, List.of(), now);
    }

    public static Result compute(
            ActivityEntity activity,
            List<PresenceEventEntity> events,
            List<RoomReplayWatchEntity> replays,
            List<ActivityLiveSessionEntity> sessions,
            LocalDateTime now) {
        LiveAgg live = liveAggregate(activity, events, sessions, now);
        ReplayAgg replay = replayAggregate(replays);

        Set<String> positiveLive = new HashSet<>();
        for (Map.Entry<String, Long> e : live.viewerSeconds.entrySet()) {
            if (e.getValue() > 0) {
                positiveLive.add(e.getKey());
            }
        }
        Set<String> union = new HashSet<>(positiveLive);
        union.addAll(replay.viewerSeconds.keySet());

        LocalDateTime trendStart = LiveSessionWindows.trendStart(activity, sessions, now);
        LocalDateTime trendEnd = LiveSessionWindows.trendEnd(activity, sessions, now, trendStart);
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

    /**
     * 各场次窗内按观众去重扫在线集合，取全局 max（D119）。
     * 只计观众；无 socket 的历史流水不参与。
     */
    public static int maxOnlinePeak(
            ActivityEntity activity,
            List<PresenceEventEntity> events,
            List<ActivityLiveSessionEntity> sessions,
            LocalDateTime now) {
        List<LiveSessionWindows.Window> windows = LiveSessionWindows.resolve(activity, sessions, now);
        if (windows.isEmpty()) {
            return 0;
        }
        // 在线：username → 未闭合 socket 数
        Map<String, Integer> openSockets = new HashMap<>();
        int peak = 0;
        for (PresenceEventEntity event : ordered(events)) {
            String viewerUsername = PresenceUserIds.viewerUsername(event.getUsername());
            String socketId = blankToNull(event.getSocketId());
            LocalDateTime time = event.getEventTime();
            if (viewerUsername == null || socketId == null || time == null) {
                continue;
            }
            if (!inAnyWindow(time, windows)) {
                // 空隙内的进出仍维护集合，但峰不计入空隙；Join/Leave 仍要配对
            }
            if (PresenceEventEntity.TYPE_JOIN.equals(event.getType())) {
                openSockets.merge(viewerUsername, 1, Integer::sum);
            } else if (PresenceEventEntity.TYPE_LEAVE.equals(event.getType())) {
                Integer n = openSockets.get(viewerUsername);
                if (n != null) {
                    if (n <= 1) {
                        openSockets.remove(viewerUsername);
                    } else {
                        openSockets.put(viewerUsername, n - 1);
                    }
                }
            }
            if (inAnyWindow(time, windows)) {
                peak = Math.max(peak, openSockets.size());
            }
        }
        return peak;
    }

    private static boolean inAnyWindow(LocalDateTime time, List<LiveSessionWindows.Window> windows) {
        for (LiveSessionWindows.Window w : windows) {
            if (!time.isBefore(w.start()) && !time.isAfter(w.end())) {
                return true;
            }
        }
        return false;
    }

    private static LiveAgg liveAggregate(
            ActivityEntity activity,
            List<PresenceEventEntity> events,
            List<ActivityLiveSessionEntity> sessions,
            LocalDateTime now) {
        List<LiveSessionWindows.Window> windows = LiveSessionWindows.resolve(activity, sessions, now);
        boolean liveInProgress = activity != null && activity.getState() != null && activity.getState() == 1;
        boolean ended = isEnded(activity);
        boolean hasClosedWindow = !windows.isEmpty();

        Map<String, Map<String, LocalDateTime>> open = new LinkedHashMap<>();
        Map<String, Long> seconds = new HashMap<>();
        long sessionCount = 0;

        for (PresenceEventEntity event : ordered(events)) {
            String viewerUsername = PresenceUserIds.viewerUsername(event.getUsername());
            String socketId = blankToNull(event.getSocketId());
            LocalDateTime time = event.getEventTime();
            if (viewerUsername == null || socketId == null || time == null) {
                continue;
            }
            Map<String, LocalDateTime> sockets = open.computeIfAbsent(viewerUsername, k -> new LinkedHashMap<>());
            if (PresenceEventEntity.TYPE_JOIN.equals(event.getType())) {
                sockets.putIfAbsent(socketId, time);
            } else if (PresenceEventEntity.TYPE_LEAVE.equals(event.getType())) {
                LocalDateTime join = sockets.remove(socketId);
                if (join != null) {
                    long sec = WatchDurationCalculator.overlapUnionSeconds(join, time, windows);
                    add(seconds, viewerUsername, sec);
                    if (sec > 0) {
                        sessionCount++;
                    }
                }
            }
        }
        for (Map.Entry<String, Map<String, LocalDateTime>> user : open.entrySet()) {
            for (LocalDateTime join : user.getValue().values()) {
                long sec = 0L;
                if (liveInProgress) {
                    sec = WatchDurationCalculator.overlapUnionSeconds(join, now, windows);
                } else if (hasClosedWindow) {
                    LocalDateTime leaveBound = windows.stream()
                            .map(LiveSessionWindows.Window::end)
                            .max(LocalDateTime::compareTo)
                            .orElse(now);
                    sec = WatchDurationCalculator.overlapUnionSeconds(join, leaveBound, windows);
                } else if (ended) {
                    sec = 0L;
                } else {
                    sec = WatchDurationCalculator.overlapUnionSeconds(join, now, windows);
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
        Map<String, Long> seconds = new HashMap<>();
        long rowCount = 0;
        long totalSec = 0;
        if (replays == null) {
            return new ReplayAgg(seconds, 0, 0);
        }
        for (RoomReplayWatchEntity row : replays) {
            if (row == null
                    || row.getUsername() == null
                    || row.getUsername().isBlank()
                    || row.getStartTime() == null
                    || row.getLastReportTime() == null) {
                continue;
            }
            long sec = Duration.between(row.getStartTime(), row.getLastReportTime()).getSeconds();
            if (sec < 0) {
                sec = 0;
            }
            rowCount++;
            totalSec += sec;
            seconds.merge(row.getUsername(), sec, Long::sum);
        }
        return new ReplayAgg(seconds, rowCount, totalSec);
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

    private static boolean isEnded(ActivityEntity activity) {
        Integer state = activity == null ? null : activity.getState();
        return state != null && (state == 3 || state == 4 || state == 5);
    }

    private static void add(Map<String, Long> seconds, String viewerUsername, long delta) {
        if (delta <= 0) {
            seconds.putIfAbsent(viewerUsername, 0L);
            return;
        }
        seconds.merge(viewerUsername, delta, Long::sum);
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

    private record LiveAgg(Map<String, Long> viewerSeconds, long sessionCount, long totalSec) {
    }

    private record ReplayAgg(Map<String, Long> viewerSeconds, long rowCount, long totalSec) {
    }
}
