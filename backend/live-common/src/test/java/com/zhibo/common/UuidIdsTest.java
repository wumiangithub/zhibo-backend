package com.zhibo.common;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UuidIdsTest {

    @Test
    void newIdIs32HexNoHyphen() {
        String id = UuidIds.newId();
        assertEquals(32, id.length());
        assertTrue(id.matches("[0-9a-f]{32}"));
    }

    @Test
    void requireAcceptsHyphenatedAndStrips() {
        assertEquals(
                "a1b2c3d4e5f67890abcdef1234567890",
                UuidIds.require("A1B2C3D4-E5F6-7890-ABCD-EF1234567890"));
    }

    @Test
    void requireAcceptsAlreadyStripped() {
        assertEquals(
                "a1b2c3d4e5f67890abcdef1234567890",
                UuidIds.require("a1b2c3d4e5f67890abcdef1234567890"));
    }

    @Test
    void requireRejectsInvalid() {
        assertThrows(IllegalArgumentException.class, () -> UuidIds.require("not-a-uuid"));
        assertThrows(IllegalArgumentException.class, () -> UuidIds.require(""));
    }
}
