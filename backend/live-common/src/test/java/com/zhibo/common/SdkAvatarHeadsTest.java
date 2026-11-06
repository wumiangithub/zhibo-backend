package com.zhibo.common;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class SdkAvatarHeadsTest {

    private static final String DEFAULT =
            "https://qklive-testcos.cjs99.com/viewer/avatar/default-avatar.png";

    @Test
    void resolveForViewer_blankUsesDefaultWithNumericV() {
        assertEquals(
                DEFAULT + "?v=12",
                SdkAvatarHeads.resolveForViewer(null, 12L, DEFAULT));
        assertEquals(
                DEFAULT + "?v=10001",
                SdkAvatarHeads.resolveForViewer("  ", 10001L, DEFAULT));
    }

    @Test
    void resolveForConsole_blankUsesDefaultWithZPrefix() {
        assertEquals(
                DEFAULT + "?v=z276",
                SdkAvatarHeads.resolveForConsole(null, 276L, DEFAULT));
    }

    @Test
    void resolveForStaff_blankUsesDefaultWithEPrefix() {
        assertEquals(
                DEFAULT + "?v=e10001",
                SdkAvatarHeads.resolveForStaff(null, 10001L, DEFAULT));
    }

    @Test
    void siteViewerAvatar_alsoAppendsV() {
        String site = "https://qklive-testcos.cjs99.com/viewer/avatar/12.png";
        assertEquals(
                site + "?v=12",
                SdkAvatarHeads.resolveForViewer(site, 12L, DEFAULT));
    }

    @Test
    void realAvatar_alwaysAppendsOrReplacesV() {
        assertEquals(
                "https://wx.cdn/a.jpg?v=12",
                SdkAvatarHeads.resolveForViewer("https://wx.cdn/a.jpg", 12L, DEFAULT));
        assertEquals(
                "https://wx.cdn/a.jpg?v=z276",
                SdkAvatarHeads.resolveForConsole("https://wx.cdn/a.jpg", 276L, DEFAULT));
        assertEquals(
                "https://wx.cdn/a.jpg?x=1&v=e9",
                SdkAvatarHeads.resolveForStaff("https://wx.cdn/a.jpg?x=1", 9L, DEFAULT));
        assertEquals(
                "https://cdn/viewer/avatar/12.png?v=12",
                SdkAvatarHeads.resolveForViewer(
                        "https://cdn/viewer/avatar/12.png?v=999", 12L, DEFAULT));
    }

    @Test
    void withUserQuery_appendsOrReplacesV() {
        assertEquals(
                "https://cdn/a.jpg?v=9",
                SdkAvatarHeads.withUserQuery("https://cdn/a.jpg", "9"));
        assertEquals(
                "https://cdn/a.jpg?x=1&v=z9",
                SdkAvatarHeads.withUserQuery("https://cdn/a.jpg?x=1", "z9"));
        assertEquals(
                "https://cdn/a.jpg?x=1&v=e9",
                SdkAvatarHeads.withUserQuery("https://cdn/a.jpg?v=3&x=1", "e9"));
        assertEquals(
                "https://cdn/a.jpg?v=9",
                SdkAvatarHeads.withUserQuery("https://cdn/a.jpg?v=3#frag", "9"));
    }

    @Test
    void blankBaseFallsBackToPlaceholder() {
        assertEquals(
                SdkAvatarHeads.PLACEHOLDER_BASE_URL + "?v=7",
                SdkAvatarHeads.withUserQuery("  ", "7"));
        assertEquals(DEFAULT, SdkAvatarHeads.PLACEHOLDER_BASE_URL);
    }

    @Test
    void normalizesZeCase() {
        assertEquals(
                DEFAULT + "?v=z276",
                SdkAvatarHeads.resolveForSign(null, "Z276", DEFAULT));
        assertEquals(
                DEFAULT + "?v=e12",
                SdkAvatarHeads.withUserQuery(DEFAULT, "E12"));
    }

    @Test
    void rejectsInvalidIdentity() {
        assertThrows(
                IllegalArgumentException.class,
                () -> SdkAvatarHeads.resolveForViewer(null, 0L, DEFAULT));
        assertThrows(
                IllegalArgumentException.class,
                () -> SdkAvatarHeads.withUserQuery(DEFAULT, "0"));
        assertThrows(
                IllegalArgumentException.class,
                () -> SdkAvatarHeads.withUserQuery(DEFAULT, "zz1"));
        assertThrows(
                IllegalArgumentException.class,
                () -> SdkAvatarHeads.withUserQuery(DEFAULT, "guest1"));
    }
}
