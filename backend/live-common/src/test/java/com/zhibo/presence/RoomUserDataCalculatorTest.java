package com.zhibo.presence;

import com.zhibo.activity.ActivityEntity;
import com.zhibo.replay.RoomReplayWatchEntity;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RoomUserDataCalculatorTest {

    @Test
    void live_countsViewersSessionsAndExcludesStaff() {
        ActivityEntity activity = activity(3);
        activity.setLiveActualStartTime(LocalDateTime.of(2026, 10, 29, 10, 0));
        activity.setLiveActualEndTime(LocalDateTime.of(2026, 10, 29, 11, 0));

        RoomUserDataCalculator.Result result = RoomUserDataCalculator.compute(
                activity,
                List.of(
                        event("12", "Join", "s1", "2026-10-29 09:50:00"),
                        event("12", "Leave", "s1", "2026-10-29 10:10:00"),
                        event("12", "Join", "s2", "2026-10-29 10:30:00"),
                        event("12", "Leave", "s2", "2026-10-29 10:50:00"),
                        event("13", "Join", "t1", "2026-10-29 10:00:00"),
                        event("13", "Leave", "t1", "2026-10-29 10:05:00"),
                        event("e10001", "Join", "staff", "2026-10-29 10:00:00"),
                        event("e10001", "Leave", "staff", "2026-10-29 10:40:00")),
                List.of(),
                LocalDateTime.of(2026, 10, 29, 12, 0));

        RoomUserDataStats stats = result.stats();
        assertEquals(2L, stats.liveViewerCount());
        assertEquals(3L, stats.liveViewCount());
        assertEquals(1800L + 300L, stats.liveTotalSec());
        assertEquals((1800L + 300L) / 2L, stats.liveAvgSec());
        assertEquals(2L, result.liveReplayUnionCount());
        assertEquals("2026-10-29 10:00:00", stats.trendStartTime());
        assertEquals("2026-10-29 11:00:00", stats.trendEndTime());
    }

    @Test
    void replay_and_union() {
        ActivityEntity activity = activity(3);
        activity.setLiveActualStartTime(LocalDateTime.of(2026, 10, 29, 10, 0));
        activity.setLiveActualEndTime(LocalDateTime.of(2026, 10, 29, 11, 0));

        RoomReplayWatchEntity r1 = replay(12L, "2026-10-29 12:00:00", "2026-10-29 12:03:00");
        RoomReplayWatchEntity r2 = replay(99L, "2026-10-29 12:00:00", "2026-10-29 12:01:00");

        RoomUserDataCalculator.Result result = RoomUserDataCalculator.compute(
                activity,
                List.of(
                        event("12", "Join", "s1", "2026-10-29 10:00:00"),
                        event("12", "Leave", "s1", "2026-10-29 10:10:00")),
                List.of(r1, r2),
                LocalDateTime.of(2026, 10, 29, 13, 0));

        RoomUserDataStats stats = result.stats();
        assertEquals(1L, stats.liveViewerCount());
        assertEquals(1L, stats.liveViewCount());
        assertEquals(2L, stats.replayViewerCount());
        assertEquals(2L, stats.replayViewCount());
        assertEquals(180L + 60L, stats.replayTotalSec());
        assertEquals(2L, result.liveReplayUnionCount());
        assertEquals(3L, stats.combinedViewCount());
        assertEquals(840L / 2L, stats.combinedAvgSec(result.liveReplayUnionCount()));
    }

    @Test
    void trendDefault_clipsToOneDay() {
        ActivityEntity activity = activity(3);
        activity.setLiveActualStartTime(LocalDateTime.of(2026, 10, 1, 10, 0));
        activity.setLiveActualEndTime(LocalDateTime.of(2026, 10, 3, 10, 0));

        RoomUserDataStats stats = RoomUserDataCalculator.compute(
                activity, List.of(), List.of(), LocalDateTime.of(2026, 10, 4, 0, 0)).stats();

        assertEquals("2026-10-01 10:00:00", stats.trendStartTime());
        assertEquals("2026-10-02 10:00:00", stats.trendEndTime());
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
        entity.setUserId(userId);
        entity.setType(type);
        entity.setSocketId(socketId);
        entity.setEventTime(LocalDateTime.parse(time.replace(' ', 'T')));
        return entity;
    }

    private static RoomReplayWatchEntity replay(long viewerId, String start, String last) {
        RoomReplayWatchEntity row = new RoomReplayWatchEntity();
        row.setViewerId(viewerId);
        row.setStartTime(LocalDateTime.parse(start.replace(' ', 'T')));
        row.setLastReportTime(LocalDateTime.parse(last.replace(' ', 'T')));
        return row;
    }
}
