package com.zhibo.presence;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PresenceSocketRosterTest {

    @Test
    void compute_leaveOneOfTwoSocketsStillOnline() {
        PresenceSocketRoster.State state = PresenceSocketRoster.compute(List.of(
                event("Join", "a", "2026-10-29 10:00:00"),
                event("Join", "b", "2026-10-29 10:01:00"),
                event("Leave", "a", "2026-10-29 10:05:00")));

        assertTrue(state.online());
        assertEquals(LocalDateTime.of(2026, 10, 29, 10, 1), state.latestOpenJoin());
        assertNull(state.latestClosedLeave());
    }

    @Test
    void compute_allSocketsClosed() {
        PresenceSocketRoster.State state = PresenceSocketRoster.compute(List.of(
                event("Join", "a", "2026-10-29 10:00:00"),
                event("Leave", "a", "2026-10-29 10:05:00")));

        assertFalse(state.online());
        assertNull(state.latestOpenJoin());
        assertEquals(LocalDateTime.of(2026, 10, 29, 10, 5), state.latestClosedLeave());
    }

    @Test
    void compute_ignoresEventsWithoutSocket() {
        PresenceEventEntity orphan = event("Join", null, "2026-10-29 10:00:00");
        PresenceSocketRoster.State state = PresenceSocketRoster.compute(List.of(orphan));
        assertFalse(state.online());
    }

    private static PresenceEventEntity event(String type, String socketId, String time) {
        PresenceEventEntity entity = new PresenceEventEntity();
        entity.setId((long) Math.abs((type + "-" + socketId + "-" + time).hashCode()));
        entity.setUserId("12");
        entity.setType(type);
        entity.setSocketId(socketId);
        entity.setEventTime(LocalDateTime.parse(time.replace(' ', 'T')));
        return entity;
    }
}
