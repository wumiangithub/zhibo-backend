package com.zhibo.presence;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PresenceUserIdsTest {

    @Test
    void parseEmail_viewerStaffGuest() {
        assertEquals("12", PresenceUserIds.parseEmail("12@qkzb.com"));
        assertEquals("e10001", PresenceUserIds.parseEmail("e10001@qkzb.com"));
        assertEquals("guest1", PresenceUserIds.parseEmail("guest1@zhibo.local"));
    }

    @Test
    void parseEmail_rejectsPasscodeAndSender() {
        assertNull(PresenceUserIds.parseEmail("pcABC@qkzb.com"));
        assertNull(PresenceUserIds.parseEmail("visit_v1"));
        assertNull(PresenceUserIds.parseEmail(""));
        assertNull(PresenceUserIds.parseEmail(null));
    }

    @Test
    void emails_roundTrip() {
        assertEquals("12@qkzb.com", PresenceUserIds.viewerEmail(12L));
        assertEquals("e10001@qkzb.com", PresenceUserIds.staffEmail(10001L));
        assertEquals(12L, PresenceUserIds.viewerId("12"));
        assertNull(PresenceUserIds.viewerId("e10001"));
        assertTrue(PresenceUserIds.isViewer("12"));
        assertThrows(IllegalArgumentException.class, () -> PresenceUserIds.staffEmail(0));
    }

    @Test
    void mayWriteRoom_viewerAndStaffOnly() {
        assertTrue(PresenceUserIds.mayWriteRoom("12"));
        assertTrue(PresenceUserIds.mayWriteRoom("e10001"));
        assertTrue(PresenceUserIds.isStaff("e10001"));
        assertFalse(PresenceUserIds.mayWriteRoom("guest1"));
        assertFalse(PresenceUserIds.mayWriteRoom(null));
        assertTrue(PresenceUserIds.isGuestEmail("guest1@zhibo.local"));
        assertFalse(PresenceUserIds.isGuestEmail("12@qkzb.com"));
    }
}
