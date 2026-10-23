package com.zhibo.live.viewer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class WechatMpClientTest {

    private ViewerAuthProperties properties;
    private Clock clock;

    @BeforeEach
    void setUp() {
        properties = new ViewerAuthProperties();
        properties.getWechat().setMpAppId("wx8ba0027c2c9e839c");
        properties.getWechat().setMpAppSecret("test-mp-secret");
        clock = Clock.fixed(Instant.parse("2026-10-23T01:00:00Z"), ZoneOffset.UTC);
    }

    @Test
    void code2Session_successIgnoresSessionKey() {
        List<String> urls = new ArrayList<>();
        WechatMpClient client = client((method, url, body) -> {
            urls.add(url);
            return "{\"openid\":\"o-abc\",\"unionid\":\"u-abc\",\"session_key\":\"sess-secret\"}";
        });

        WechatMpClient.WechatSession session = client.code2Session("js-code");

        assertEquals("o-abc", session.openid());
        assertEquals("u-abc", session.unionid());
        assertEquals(1, urls.size());
        assertTrue(urls.get(0).contains("/sns/jscode2session"));
        assertFalse(session.toString().contains("sess-secret"));
    }

    @Test
    void code2Session_errcode_failsWithoutLeakingSecret() {
        WechatMpClient client = client((method, url, body) -> "{\"errcode\":40029,\"errmsg\":\"invalid code\"}");

        ViewerAuthException ex = assertThrows(ViewerAuthException.class, () -> client.code2Session("js-code"));

        assertEquals(400, ex.getCode());
        assertEquals("微信授权失败，请重试", ex.getMessage());
        assertFalse(ex.getMessage().contains("test-mp-secret"));
        assertFalse(ex.getMessage().contains("js-code"));
        assertFalse(ex.getMessage().contains("40029"));
    }

    @Test
    void getPhoneNumber_cachesClientCredentialToken() {
        List<String> urls = new ArrayList<>();
        WechatMpClient client = client((method, url, body) -> {
            urls.add(url);
            if (url.contains("/cgi-bin/token")) {
                return "{\"access_token\":\"tok-1\",\"expires_in\":7200}";
            }
            return "{\"errcode\":0,\"phone_info\":{\"purePhoneNumber\":\"13612348079\",\"countryCode\":86}}";
        });

        WechatMpClient.WechatPhone first = client.getPhoneNumber("p1");
        WechatMpClient.WechatPhone second = client.getPhoneNumber("p2");

        assertEquals("13612348079", first.purePhoneNumber());
        assertEquals("86", first.countryCode());
        assertEquals("86", second.countryCode());
        assertEquals(1, urls.stream().filter(url -> url.contains("/cgi-bin/token")).count());
        assertEquals(2, urls.stream().filter(url -> url.contains("getuserphonenumber")).count());
    }

    private WechatMpClient client(WechatMpClient.WechatHttp http) {
        return new WechatMpClient(properties, new ObjectMapper(), clock, http);
    }
}
