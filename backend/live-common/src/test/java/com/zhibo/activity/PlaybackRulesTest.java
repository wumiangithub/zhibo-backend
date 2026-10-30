package com.zhibo.activity;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlaybackRulesTest {

    @Test
    void forever_notExpired() {
        ActivityEntity a = new ActivityEntity();
        a.setPlaybackValidityType("forever");
        assertFalse(PlaybackRules.isExpired(a, LocalDateTime.of(2026, 10, 30, 12, 0)));
    }

    @Test
    void days_expired_byActualEnd() {
        ActivityEntity a = new ActivityEntity();
        a.setPlaybackValidityType("days");
        a.setPlaybackValidityDays(7);
        a.setLiveActualEndTime(LocalDateTime.of(2026, 10, 1, 12, 0));
        assertTrue(PlaybackRules.isExpired(a, LocalDateTime.of(2026, 10, 29, 12, 0)));
    }

    @Test
    void days_within_window() {
        ActivityEntity a = new ActivityEntity();
        a.setPlaybackValidityType("days");
        a.setPlaybackValidityDays(7);
        a.setLiveActualEndTime(LocalDateTime.of(2026, 10, 25, 12, 0));
        assertFalse(PlaybackRules.isExpired(a, LocalDateTime.of(2026, 10, 29, 12, 0)));
    }

    @Test
    void assertReplayAllowed_disabled() {
        ActivityEntity a = new ActivityEntity();
        a.setPlaybackEnabled(false);
        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> PlaybackRules.assertReplayAllowed(a, LocalDateTime.of(2026, 10, 30, 12, 0)));
        assertEquals("回放未开启", ex.getMessage());
    }
}
