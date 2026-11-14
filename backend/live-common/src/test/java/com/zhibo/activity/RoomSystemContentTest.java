package com.zhibo.activity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class RoomSystemContentTest {

    @Test
    void roundTrip() {
        RoomSystemContent src = new RoomSystemContent("请遵守\"规则\"", true);
        RoomSystemContent parsed = RoomSystemContent.parse(src.toJson());
        assertNotNull(parsed);
        assertEquals("请遵守\"规则\"", parsed.text());
        assertTrue(parsed.pin());
    }

    @Test
    void withPinClearsFlag() {
        RoomSystemContent src = new RoomSystemContent("公告", true).withPin(false);
        assertFalse(src.pin());
        assertEquals("公告", src.text());
        RoomSystemContent parsed = RoomSystemContent.parse(src.toJson());
        assertNotNull(parsed);
        assertFalse(parsed.pin());
    }

    @Test
    void rejectBlankText() {
        assertNull(RoomSystemContent.parse("{\"text\":\"\",\"pin\":true}"));
        assertNull(RoomSystemContent.parse("{\"pin\":true}"));
    }
}
