package com.zhibo.vhall.util;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VhallCallbackSignUtilTest {

    @Test
    void sign_skipsSignatureAndNestedContextAndIsOrderIndependent() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("type", "Join");
        body.put("method_id", 20);
        body.put("mid", "abc");
        body.put("signature", "should-ignore");
        body.put("context", Map.of("nickname", "小龙女"));

        String sign = VhallCallbackSignUtil.sign("secret", body);
        assertEquals(32, sign.length());
        assertTrue(VhallCallbackSignUtil.matches("secret", body, sign));
        assertTrue(VhallCallbackSignUtil.matches("secret", body, sign.toUpperCase()));
        assertFalse(VhallCallbackSignUtil.matches("secret", body, "deadbeef"));

        Map<String, Object> shuffled = new LinkedHashMap<>();
        shuffled.put("mid", "abc");
        shuffled.put("type", "Join");
        shuffled.put("method_id", 20);
        shuffled.put("context", Map.of("nickname", "小龙女"));
        assertEquals(sign, VhallCallbackSignUtil.sign("secret", shuffled));
    }
}
