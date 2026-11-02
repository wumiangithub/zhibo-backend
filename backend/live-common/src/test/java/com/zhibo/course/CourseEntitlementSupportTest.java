package com.zhibo.course;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class CourseEntitlementSupportTest {

    @Test
    void resolveState_cancelledIsInvalid() {
        assertEquals(
                "invalid",
                CourseEntitlementSupport.resolveState(
                        0, "days", LocalDateTime.now().plusDays(1), LocalDateTime.now()));
    }

    @Test
    void resolveState_daysPastIsExpired() {
        LocalDateTime now = LocalDateTime.of(2026, 11, 2, 12, 0);
        assertEquals(
                "expired",
                CourseEntitlementSupport.resolveState(
                        1, "days", now.minusDays(1), now));
    }

    @Test
    void resolveState_fixedPastIsExpired() {
        LocalDateTime now = LocalDateTime.of(2026, 11, 2, 12, 0);
        assertEquals(
                "expired",
                CourseEntitlementSupport.resolveState(
                        1, "fixed", now.minusSeconds(1), now));
    }

    @Test
    void resolveState_longIgnoresPastExpire() {
        LocalDateTime now = LocalDateTime.of(2026, 11, 2, 12, 0);
        assertEquals(
                "valid",
                CourseEntitlementSupport.resolveState(
                        1, "long", now.minusDays(1), now));
    }

    @Test
    void expireAtJoin_daysAndFixed() {
        CourseEntity course = new CourseEntity();
        course.setAccessValidityType("days");
        course.setAccessValidityDays(7);
        LocalDateTime from = LocalDateTime.of(2026, 1, 1, 0, 0);
        assertEquals(from.plusDays(7), CourseEntitlementSupport.expireAtJoin(course, from));

        LocalDateTime fixed = LocalDateTime.of(2026, 11, 14, 0, 0);
        course.setAccessValidityType("fixed");
        course.setAccessValidityFixedTime(fixed);
        assertEquals(fixed, CourseEntitlementSupport.expireAtJoin(course, from));

        course.setAccessValidityType("long");
        assertNull(CourseEntitlementSupport.expireAtJoin(course, from));
    }

    @Test
    void expireDisplay_longShowsLong() {
        assertEquals("长期", CourseEntitlementSupport.expireDisplay("long", "2026-01-01 00:00:00"));
        assertEquals("—", CourseEntitlementSupport.expireDisplay("days", null));
        assertEquals("2026-01-08 00:00:00", CourseEntitlementSupport.expireDisplay("days", "2026-01-08 00:00:00"));
        assertEquals("2026-11-14 00:00:00", CourseEntitlementSupport.expireDisplay("fixed", "2026-11-14 00:00:00"));
    }
}
