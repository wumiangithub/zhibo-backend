package com.zhibo.presence;

import com.zhibo.activity.AttendanceTimes;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 把进出事件聚合成时间桶。
 * <ul>
 *   <li>{@code enter}/{@code leave}：该桶内 Join/Leave <strong>事件条数</strong></li>
 *   <li>{@code online}：桶结束时「当前在房」人数——按 {@code user_id} 去重
 *       （同一人反复 Join 不加人数；Leave 才移出）</li>
 * </ul>
 */
public final class PresenceTrendCalculator {

    private PresenceTrendCalculator() {
    }

    public static List<PresenceTrendPoint> aggregate(
            List<PresenceEventEntity> events,
            LocalDateTime start,
            LocalDateTime end,
            int bucketSeconds) {
        if (bucketSeconds <= 0) {
            throw new IllegalArgumentException("bucketSeconds 须为正整数");
        }
        if (!end.isAfter(start)) {
            return List.of();
        }

        // 每桶：enter / leave 条数
        Map<LocalDateTime, int[]> counts = new HashMap<>();
        // 每桶内按时间排序的事件，用于维护在房集合
        Map<LocalDateTime, List<PresenceEventEntity>> byBucket = new HashMap<>();

        if (events != null) {
            List<PresenceEventEntity> ordered = new ArrayList<>(events);
            ordered.sort(Comparator
                    .comparing(PresenceEventEntity::getEventTime, Comparator.nullsLast(Comparator.naturalOrder()))
                    .thenComparing(e -> e.getId() == null ? "" : e.getId()));

            for (PresenceEventEntity event : ordered) {
                LocalDateTime t = event.getEventTime();
                if (t == null || t.isBefore(start) || !t.isBefore(end)) {
                    continue;
                }
                LocalDateTime bucketStart = bucketStartOf(start, t, bucketSeconds);
                int[] pair = counts.computeIfAbsent(bucketStart, key -> new int[2]);
                if (PresenceEventEntity.TYPE_JOIN.equals(event.getType())) {
                    pair[0]++;
                } else if (PresenceEventEntity.TYPE_LEAVE.equals(event.getType())) {
                    pair[1]++;
                }
                byBucket.computeIfAbsent(bucketStart, key -> new ArrayList<>()).add(event);
            }
        }

        List<PresenceTrendPoint> points = new ArrayList<>();
        Set<String> onlineUsers = new HashSet<>();
        LocalDateTime cursor = start;
        while (cursor.isBefore(end)) {
            int[] pair = counts.getOrDefault(cursor, new int[]{0, 0});
            List<PresenceEventEntity> bucketEvents = byBucket.getOrDefault(cursor, List.of());
            for (PresenceEventEntity event : bucketEvents) {
                applyToOnlineSet(onlineUsers, event);
            }
            points.add(new PresenceTrendPoint(
                    AttendanceTimes.format(cursor),
                    onlineUsers.size(),
                    pair[0],
                    pair[1]));
            cursor = cursor.plusSeconds(bucketSeconds);
        }
        return points;
    }

    private static LocalDateTime bucketStartOf(LocalDateTime rangeStart, LocalDateTime eventTime, int bucketSeconds) {
        long secondsFromStart = java.time.Duration.between(rangeStart, eventTime).getSeconds();
        long bucketIndex = secondsFromStart / bucketSeconds;
        return rangeStart.plusSeconds(bucketIndex * bucketSeconds);
    }

    private static void applyToOnlineSet(Set<String> onlineUsers, PresenceEventEntity event) {
        String userId = event.getUserId();
        if (userId == null || userId.isBlank()) {
            return;
        }
        if (PresenceEventEntity.TYPE_JOIN.equals(event.getType())) {
            onlineUsers.add(userId);
        } else if (PresenceEventEntity.TYPE_LEAVE.equals(event.getType())) {
            onlineUsers.remove(userId);
        }
    }
}
