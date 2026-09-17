package com.zhibo.vhall.util;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 对照官方文档示例：
 * md5("user_secret_keyapp_keyuser_app_keysign_type0signed_at1641469560webinar_id123456user_secret_key")
 * = 68e99b89f6bdc1d751dd8a6f15c609c8
 */
class VhallSignUtilTest {

    @Test
    void sign_matchesOfficialDocExample() {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("webinar_id", "123456");
        params.put("signed_at", 1641469560);
        params.put("sign_type", 0);
        params.put("app_key", "user_app_key");

        String sign = VhallSignUtil.sign("user_secret_key", params);
        assertEquals("68e99b89f6bdc1d751dd8a6f15c609c8", sign);
    }

    @Test
    void sign_skipsSignFieldAndIgnoresArrayValues() {
        Map<String, Object> params = Map.of(
                "app_key", "k",
                "sign", "should-be-ignored",
                "ids", List.of(1, 2, 3),
                "signed_at", "1",
                "sign_type", 0
        );

        // secret + app_keyk + ids + sign_type0 + signed_at1 + secret
        String expected = VhallSignUtil.md5Hex("sapp_keykidssign_type0signed_at1s");
        assertEquals(expected, VhallSignUtil.sign("s", params));
    }

    @Test
    void sign_rejectsBlankSecret() {
        assertThrows(IllegalArgumentException.class,
                () -> VhallSignUtil.sign(" ", Map.of("a", "1")));
    }
}
