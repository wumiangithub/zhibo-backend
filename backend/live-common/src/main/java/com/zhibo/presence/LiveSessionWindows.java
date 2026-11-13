package com.zhibo.presence;

import com.zhibo.activity.ActivityEntity;
import com.zhibo.activity.ActivityLiveSessionEntity;
import com.zhibo.activity.AttendanceTimes;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * D119：把活动场次解析为可求交的时间窗并集。
 * 有场次行时用各段；无场次回退 {@code live_actual_*} / 计划时间。
 */
public final class LiveSessionWindows {

    public record Window(LocalDateTime start, LocalDateTime end) {
    }

    private LiveSessionWindows() {
    }

    /**
     * 解析直播区间并集。
     * <ul>
     *   <li>有场次：每段 {@code [start, end]}；{@code end} 空且直播中 → {@code now}；{@code end} 空且已结束 → 该段不计</li>
     *   <li>无场次：单区间回退旧逻辑</li>
     * </ul>
     */
    public static List<Window> resolve(
            ActivityEntity activity,
            List<ActivityLiveSessionEntity> sessions,
            LocalDateTime now) {
        List<ActivityLiveSessionEntity> rows = sessions == null ? List.of() : sessions;
        if (!rows.isEmpty()) {
            boolean liveInProgress = activity != null && activity.getState() != null && activity.getState() == 1;
            List<Window> windows = new ArrayList<>();
            for (ActivityLiveSessionEntity row : rows) {
                if (row == null || row.getStartTime() == null) {
                    continue;
                }
                LocalDateTime end = row.getEndTime();
                if (end == null) {
                    if (liveInProgress) {
                        end = now;
                    } else {
                        continue;
                    }
                }
                if (!end.isAfter(row.getStartTime())) {
                    continue;
                }
                windows.add(new Window(row.getStartTime(), end));
            }
            windows.sort(Comparator.comparing(Window::start));
            return windows;
        }
        LocalDateTime start = fallbackStart(activity);
        LocalDateTime end = fallbackEnd(activity, now);
        if (start == null || end == null || !end.isAfter(start)) {
            return List.of();
        }
        return List.of(new Window(start, end));
    }

    /** 走势默认窗起点：各段最早 start，否则回退汇总/计划。 */
    public static LocalDateTime trendStart(
            ActivityEntity activity,
            List<ActivityLiveSessionEntity> sessions,
            LocalDateTime now) {
        LocalDateTime min = null;
        if (sessions != null) {
            for (ActivityLiveSessionEntity row : sessions) {
                if (row != null && row.getStartTime() != null
                        && (min == null || row.getStartTime().isBefore(min))) {
                    min = row.getStartTime();
                }
            }
        }
        if (min != null) {
            return min;
        }
        if (activity != null && activity.getLiveActualStartTime() != null) {
            return activity.getLiveActualStartTime();
        }
        LocalDateTime planned = parseNullable(activity == null ? null : activity.getStartTime());
        if (planned != null) {
            return planned;
        }
        return now.minusHours(1);
    }

    /** 走势默认窗终点：各段最晚 end（空且直播中→now），否则回退汇总/计划。 */
    public static LocalDateTime trendEnd(
            ActivityEntity activity,
            List<ActivityLiveSessionEntity> sessions,
            LocalDateTime now,
            LocalDateTime start) {
        LocalDateTime max = null;
        boolean liveInProgress = activity != null && activity.getState() != null && activity.getState() == 1;
        if (sessions != null && !sessions.isEmpty()) {
            for (ActivityLiveSessionEntity row : sessions) {
                if (row == null) {
                    continue;
                }
                LocalDateTime end = row.getEndTime();
                if (end == null && liveInProgress) {
                    end = now;
                }
                if (end != null && (max == null || end.isAfter(max))) {
                    max = end;
                }
            }
            if (max != null) {
                return max;
            }
        }
        if (activity != null && activity.getLiveActualEndTime() != null) {
            return activity.getLiveActualEndTime();
        }
        if (liveInProgress) {
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

    private static LocalDateTime fallbackStart(ActivityEntity activity) {
        if (activity == null) {
            return null;
        }
        if (activity.getLiveActualStartTime() != null) {
            return activity.getLiveActualStartTime();
        }
        return parseNullable(activity.getStartTime());
    }

    private static LocalDateTime fallbackEnd(ActivityEntity activity, LocalDateTime now) {
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
}
