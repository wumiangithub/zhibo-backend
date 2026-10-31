package com.zhibo.activity;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AccessRulesTest {

    @Test
    void long_notExpired() {
        ActivityEntity a = new ActivityEntity();
        a.setAccessValidityType("long");
        a.setLiveActualEndTime(LocalDateTime.of(2026, 10, 1, 12, 0));
        assertFalse(AccessRules.isAccessExpired(a, LocalDateTime.of(2026, 12, 1, 12, 0)));
    }

    @Test
    void days_beforeActualEnd_notExpired() {
        ActivityEntity a = new ActivityEntity();
        a.setAccessValidityType("days");
        a.setAccessValidityDays(7);
        a.setLiveActualEndTime(null);
        assertFalse(AccessRules.isAccessExpired(a, LocalDateTime.of(2026, 12, 1, 12, 0)));
    }

    @Test
    void days_withinWindow() {
        ActivityEntity a = new ActivityEntity();
        a.setAccessValidityType("days");
        a.setAccessValidityDays(7);
        a.setLiveActualEndTime(LocalDateTime.of(2026, 10, 25, 12, 0));
        assertFalse(AccessRules.isAccessExpired(a, LocalDateTime.of(2026, 10, 29, 12, 0)));
    }

    @Test
    void days_expired() {
        ActivityEntity a = new ActivityEntity();
        a.setAccessValidityType("days");
        a.setAccessValidityDays(7);
        a.setLiveActualEndTime(LocalDateTime.of(2026, 10, 1, 12, 0));
        assertTrue(AccessRules.isAccessExpired(a, LocalDateTime.of(2026, 10, 9, 12, 0)));
    }

    @Test
    void requiresGate_onlyEncryptedOrSpecifiedWhenSellAlone() {
        ActivityEntity a = new ActivityEntity();
        a.setSellAlone(true);
        a.setSellType("encrypted");
        assertTrue(AccessRules.requiresGate(a));
        a.setSellType("specified");
        assertTrue(AccessRules.requiresGate(a));
        a.setSellType("free");
        assertFalse(AccessRules.requiresGate(a));
        a.setSellType("paid");
        assertFalse(AccessRules.requiresGate(a));
        a.setSellType("encrypted");
        a.setSellAlone(false);
        assertFalse(AccessRules.requiresGate(a));
    }
}
