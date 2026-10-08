package com.zhibo.presence;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PresenceTrendCalculatorTest {

    @Test
    void aggregate_matchesProductChartShape() {
        LocalDateTime start = LocalDateTime.of(2026, 9, 24, 11, 0, 0);
        LocalDateTime end = LocalDateTime.of(2026, 9, 24, 13, 0, 0);

        List<PresenceEventEntity> events = List.of(
                event("u1", PresenceEventEntity.TYPE_JOIN, "2026-09-24 11:25:10"),
                event("u1", PresenceEventEntity.TYPE_LEAVE, "2026-09-24 12:20:05"),
                event("u2", PresenceEventEntity.TYPE_JOIN, "2026-09-24 12:25:00"),
                event("u3", PresenceEventEntity.TYPE_JOIN, "2026-09-24 12:30:00"));

        List<PresenceTrendPoint> points = PresenceTrendCalculator.aggregate(events, start, end, 60);

        assertEquals(120, points.size());
        assertEquals(new PresenceTrendPoint("2026-09-24 11:00:00", 0, 0, 0), points.get(0));
        assertEquals(new PresenceTrendPoint("2026-09-24 11:25:00", 1, 1, 0), points.get(25));
        assertEquals(new PresenceTrendPoint("2026-09-24 12:19:00", 1, 0, 0), points.get(79));
        assertEquals(new PresenceTrendPoint("2026-09-24 12:20:00", 0, 0, 1), points.get(80));
        assertEquals(new PresenceTrendPoint("2026-09-24 12:25:00", 1, 1, 0), points.get(85));
        assertEquals(new PresenceTrendPoint("2026-09-24 12:30:00", 2, 1, 0), points.get(90));
        assertEquals(new PresenceTrendPoint("2026-09-24 12:59:00", 2, 0, 0), points.get(119));
    }

    @Test
    void aggregate_sameUserRepeatedJoinDoesNotInflateOnline() {
        LocalDateTime start = LocalDateTime.of(2026, 10, 8, 17, 29, 0);
        LocalDateTime end = LocalDateTime.of(2026, 10, 8, 17, 34, 0);

        // 与线上真实脏数据同形：同一人多次 Join + 另一人 Join，无 Leave
        List<PresenceEventEntity> events = List.of(
                event("assistant", PresenceEventEntity.TYPE_JOIN, "2026-10-08 17:29:25"),
                event("assistant", PresenceEventEntity.TYPE_JOIN, "2026-10-08 17:30:23"),
                event("assistant", PresenceEventEntity.TYPE_JOIN, "2026-10-08 17:31:25"),
                event("viewer", PresenceEventEntity.TYPE_JOIN, "2026-10-08 17:31:36"),
                event("assistant", PresenceEventEntity.TYPE_JOIN, "2026-10-08 17:33:14"));

        List<PresenceTrendPoint> points = PresenceTrendCalculator.aggregate(events, start, end, 60);

        assertEquals(new PresenceTrendPoint("2026-10-08 17:29:00", 1, 1, 0), points.get(0));
        assertEquals(new PresenceTrendPoint("2026-10-08 17:30:00", 1, 1, 0), points.get(1));
        // enter=2（助理又 Join + 观众 Join），online 只到 2
        assertEquals(new PresenceTrendPoint("2026-10-08 17:31:00", 2, 2, 0), points.get(2));
        assertEquals(new PresenceTrendPoint("2026-10-08 17:32:00", 2, 0, 0), points.get(3));
        assertEquals(new PresenceTrendPoint("2026-10-08 17:33:00", 2, 1, 0), points.get(4));
    }

    @Test
    void aggregate_clampsOnlineAtZero() {
        LocalDateTime start = LocalDateTime.of(2026, 9, 30, 10, 0, 0);
        LocalDateTime end = LocalDateTime.of(2026, 9, 30, 10, 3, 0);
        List<PresenceEventEntity> events = List.of(
                event("u1", PresenceEventEntity.TYPE_LEAVE, "2026-09-30 10:00:10"),
                event("u1", PresenceEventEntity.TYPE_JOIN, "2026-09-30 10:01:00"));

        List<PresenceTrendPoint> points = PresenceTrendCalculator.aggregate(events, start, end, 60);

        assertEquals(0, points.get(0).online());
        assertEquals(1, points.get(0).leave());
        assertEquals(1, points.get(1).online());
        assertEquals(1, points.get(1).enter());
    }

    private static PresenceEventEntity event(String userId, String type, String time) {
        PresenceEventEntity e = new PresenceEventEntity();
        e.setId(userId + "-" + type + "-" + time);
        e.setUserId(userId);
        e.setType(type);
        e.setEventTime(LocalDateTime.parse(time.replace(' ', 'T')));
        return e;
    }
}
