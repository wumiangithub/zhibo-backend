package com.zhibo.presence;

import com.zhibo.activity.ActivityEntity;
import com.zhibo.activity.ActivityLiveSessionEntity;
import com.zhibo.replay.RoomReplayWatchEntity;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class WatchDurationCalculatorTest {

    @Test
    void live_clipsToActualWindowAndSumsSockets() {
        ActivityEntity activity = activity(3);
        activity.setLiveActualStartTime(LocalDateTime.of(2026, 10, 29, 10, 0));
        activity.setLiveActualEndTime(LocalDateTime.of(2026, 10, 29, 11, 0));

        Map<String, Long> live = WatchDurationCalculator.liveSeconds(
                activity,
                List.of(
                        event("12", "Join", "s1", "2026-10-29 09:50:00"),
                        event("12", "Leave", "s1", "2026-10-29 10:10:00"),
                        event("12", "Join", "s2", "2026-10-29 10:30:00"),
                        event("12", "Leave", "s2", "2026-10-29 10:50:00"),
                        event("e10001", "Join", "staff", "2026-10-29 10:00:00"),
                        event("e10001", "Leave", "staff", "2026-10-29 10:40:00")),
                LocalDateTime.of(2026, 10, 29, 12, 0));

        assertEquals(1800L, live.get("12"));
        assertNull(live.get(10001L));
    }

    @Test
    void live_openSegmentUsesNowWhileLive() {
        ActivityEntity activity = activity(1);
        activity.setLiveActualStartTime(LocalDateTime.of(2026, 10, 29, 10, 0));
        LocalDateTime now = LocalDateTime.of(2026, 10, 29, 10, 5);

        Map<String, Long> live = WatchDurationCalculator.liveSeconds(
                activity,
                List.of(event("12", "Join", "s1", "2026-10-29 10:00:00")),
                now);

        assertEquals(300L, live.get("12"));
    }

    @Test
    void live_endedWithoutLeaveOrActualEndIsZero() {
        ActivityEntity activity = activity(3);
        activity.setStartTime("2026-10-29 10:00:00");

        Map<String, Long> live = WatchDurationCalculator.liveSeconds(
                activity,
                List.of(event("12", "Join", "s1", "2026-10-29 10:00:00")),
                LocalDateTime.of(2026, 10, 29, 12, 0));

        assertEquals(0L, live.getOrDefault("12", 0L));
    }

    @Test
    void live_openSegmentCapsAtActualEnd() {
        ActivityEntity activity = activity(3);
        activity.setLiveActualStartTime(LocalDateTime.of(2026, 10, 29, 10, 0));
        activity.setLiveActualEndTime(LocalDateTime.of(2026, 10, 29, 11, 0));

        Map<String, Long> live = WatchDurationCalculator.liveSeconds(
                activity,
                List.of(event("12", "Join", "s1", "2026-10-29 10:00:00")),
                LocalDateTime.of(2026, 10, 29, 12, 0));

        assertEquals(3600L, live.get("12"));
    }

    @Test
    void replay_usesLastReportMinusStart() {
        RoomReplayWatchEntity row = new RoomReplayWatchEntity();
        row.setUsername("12");
        row.setStartTime(LocalDateTime.of(2026, 10, 29, 10, 0));
        row.setLastReportTime(LocalDateTime.of(2026, 10, 29, 10, 3));

        Map<String, WatchDurationCalculator.Durations> out = WatchDurationCalculator.byViewer(
                activity(3),
                List.of(),
                List.of(row),
                LocalDateTime.of(2026, 10, 29, 12, 0));

        assertEquals(180L, out.get("12").replaySec());
        assertEquals(180L, out.get("12").totalSec());
        assertEquals(0L, out.get("12").liveSec());
    }

    @Test
    void live_twoSessionsSkipsGapBetweenThem() {
        ActivityEntity activity = activity(3);
        List<ActivityLiveSessionEntity> sessions = List.of(
                session(1L, "2026-10-29T10:00:00", "2026-10-29T11:00:00"),
                session(2L, "2026-10-29T14:00:00", "2026-10-29T15:00:00"));

        // 全程在线 10:00～15:00，中间 11:00～14:00 空隙不计
        Map<String, Long> live = WatchDurationCalculator.liveSeconds(
                activity,
                List.of(
                        event("12", "Join", "s1", "2026-10-29 10:00:00"),
                        event("12", "Leave", "s1", "2026-10-29 15:00:00")),
                sessions,
                LocalDateTime.of(2026, 10, 29, 16, 0));

        assertEquals(7200L, live.get("12"));
    }

    private static ActivityLiveSessionEntity session(long switchId, String start, String end) {
        ActivityLiveSessionEntity row = new ActivityLiveSessionEntity();
        row.setSwitchId(switchId);
        row.setStartTime(LocalDateTime.parse(start));
        row.setEndTime(LocalDateTime.parse(end));
        return row;
    }

    private static ActivityEntity activity(int state) {
        ActivityEntity entity = new ActivityEntity();
        entity.setId("a1b2c3d4e5f67890abcdef1234567890");
        entity.setState(state);
        entity.setStartTime("2026-10-29 09:00:00");
        return entity;
    }

    private static PresenceEventEntity event(String userId, String type, String socketId, String time) {
        PresenceEventEntity entity = new PresenceEventEntity();
        entity.setId((long) Math.abs((userId + "-" + type + "-" + socketId + "-" + time).hashCode()));
        entity.setUsername(userId);
        entity.setType(type);
        entity.setSocketId(socketId);
        entity.setEventTime(LocalDateTime.parse(time.replace(' ', 'T')));
        return entity;
    }
}
