package com.zhibo.live.watch;

import com.zhibo.live.watch.dto.WatchSdkResponse;
import com.zhibo.vhall.client.VhallClient;
import com.zhibo.vhall.config.VhallProperties;
import com.zhibo.vhall.util.VhallSignUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * {@link WatchSdkService} 单测：固定时钟 + 固定凭证，验证 3.9.1 签名字段。
 */
@ExtendWith(MockitoExtension.class)
class WatchSdkServiceTest {

    @Mock
    private VhallClient vhallClient;

    private VhallProperties properties;
    private Clock fixedClock;

    private WatchSdkService watchSdkService;

    @BeforeEach
    void setUp() {
        properties = new VhallProperties();
        properties.setAppKey("appkey456");
        properties.setAppSecret("secret123");

        fixedClock = new Clock() {
            @Override
            public ZoneOffset getZone() {
                return ZoneOffset.UTC;
            }

            @Override
            public Clock withZone(java.time.ZoneId zone) {
                return this;
            }

            @Override
            public Instant instant() {
                return Instant.ofEpochSecond(1737200000L);
            }
        };

        watchSdkService = new WatchSdkService(vhallClient, properties, fixedClock);
    }

    @Test
    void sdk_signsV391FieldsWithEmailOnly() {
        when(vhallClient.postForData(eq(WatchSdkService.PATH_INFO), anyMap(), eq(Map.class)))
                .thenReturn(Map.of(
                        "subject", "直播课",
                        "webinar_state", 1,
                        "webinar_type", 2,
                        "full_embed_share_link", "https://live.example/embed/55"
                ));

        WatchSdkResponse response = watchSdkService.sdk(55L, "guestABC", "小明");

        Map<String, Object> expectedSignParams = new LinkedHashMap<>();
        expectedSignParams.put("app_key", "appkey456");
        expectedSignParams.put("signedat", "1737200000");
        expectedSignParams.put("webinar_id", "55");
        expectedSignParams.put("email", "guestABC@zhibo.local");
        expectedSignParams.put("username", "小明");
        String expectedSign = VhallSignUtil.sign("secret123", expectedSignParams);

        assertEquals(expectedSign, response.getSdk().getSign());
        assertEquals("1737200000", response.getSdk().getSignedAt());
        assertEquals(0, response.getSdk().getSignType());
        assertEquals("guestABC", response.getSdk().getAccount());
        assertEquals("guestABC@zhibo.local", response.getSdk().getEmail());
        assertEquals("小明", response.getSdk().getUsername());
        assertEquals("55", response.getSdk().getWebinarId());
        assertEquals("appkey456", response.getSdk().getAppKey());
        assertEquals(WatchSdkService.SDK_SCRIPT_URL, response.getSdk().getScriptUrl());
        assertNull(response.getSdk().getJqueryUrl());
        assertTrue(response.getSdk().getScriptUrl().contains("3.9.1"));
    }

    @Test
    void sdk_signIs32LowercaseHexAndNotSecret() {
        when(vhallClient.postForData(eq(WatchSdkService.PATH_INFO), anyMap(), eq(Map.class)))
                .thenReturn(Map.of(
                        "subject", "课",
                        "full_embed_share_link", "https://live.example/embed/1"
                ));

        WatchSdkResponse response = watchSdkService.sdk(1L, "abc", null);
        String sign = response.getSdk().getSign();
        assertNotNull(sign);
        assertTrue(sign.matches("^[0-9a-f]{32}$"), "sign 应为 32 位小写 hex");
        assertNotEquals("secret123", sign);
    }

    @Test
    void sdk_generatesGuestIdAndDefaultNicknameAndEmbedFallback() {
        when(vhallClient.postForData(eq(WatchSdkService.PATH_INFO), anyMap(), eq(Map.class)))
                .thenReturn(Map.of(
                        "subject", "课",
                        "full_embed_share_link", "https://live.example/embed/1"
                ));

        WatchSdkResponse response = watchSdkService.sdk(1L, null, null);
        assertEquals(WatchIdentity.DEFAULT_NICKNAME, response.getNickname());
        assertNotNull(response.getGuestId());
        assertTrue(response.getGuestId().matches("^[A-Za-z0-9]+$"));
        assertEquals(response.getGuestId(), response.getSdk().getAccount());
        assertEquals(WatchIdentity.DEFAULT_NICKNAME, response.getSdk().getUsername());
        assertTrue(response.getEmbedUrl().startsWith("https://live.example/embed/1?"));
        assertTrue(response.getEmbedUrl().contains("email=" + response.getGuestId()));
        assertTrue(response.getEmbedUrl().contains("zhibo.local"));
    }

    @Test
    void sdk_mapsStartTimeAndOmitsPlaceholderEndTime() {
        when(vhallClient.postForData(eq(WatchSdkService.PATH_INFO), anyMap(), eq(Map.class)))
                .thenReturn(Map.of(
                        "subject", "预告课",
                        "webinar_state", 2,
                        "webinar_type", 2,
                        "start_time", "2026-09-18 20:00:00",
                        "end_time", "0000-00-00 00:00:00",
                        "full_embed_share_link", "https://live.example/embed/55"
                ));

        WatchSdkResponse response = watchSdkService.sdk(55L, "guestABC", "小明");
        assertEquals("2026-09-18 20:00:00", response.getStartTime());
        assertNull(response.getEndTime());
        assertEquals(2, response.getState());
    }

    @Test
    void sdk_mapsRealEndTime() {
        when(vhallClient.postForData(eq(WatchSdkService.PATH_INFO), anyMap(), eq(Map.class)))
                .thenReturn(Map.of(
                        "subject", "已结束",
                        "webinar_state", 3,
                        "start_time", "2026-09-18 20:00:00",
                        "end_time", "2026-09-18 21:30:00",
                        "full_embed_share_link", "https://live.example/embed/55"
                ));

        WatchSdkResponse response = watchSdkService.sdk(55L, "guestABC", null);
        assertEquals("2026-09-18 20:00:00", response.getStartTime());
        assertEquals("2026-09-18 21:30:00", response.getEndTime());
        assertEquals(3, response.getState());
    }

    @Test
    void sdk_rejectsInvalidGuestId() {
        assertThrows(IllegalArgumentException.class,
                () -> watchSdkService.sdk(1L, "bad-id", null));
    }

    @Test
    void sdk_rejectsInvalidActivityId() {
        assertThrows(IllegalArgumentException.class,
                () -> watchSdkService.sdk(0L, "abc", null));
    }

    @Test
    void sdk_requiresFullEmbedShareLink() {
        when(vhallClient.postForData(eq(WatchSdkService.PATH_INFO), anyMap(), eq(Map.class)))
                .thenReturn(Map.of("subject", "无嵌入"));

        assertThrows(IllegalStateException.class,
                () -> watchSdkService.sdk(1L, "abc", null));
    }

    @Test
    void sdk_requiresSecretKey() {
        VhallProperties noSecret = new VhallProperties();
        noSecret.setAppKey("appkey456");
        noSecret.setAppSecret("");
        WatchSdkService service = new WatchSdkService(vhallClient, noSecret, fixedClock);

        assertThrows(IllegalStateException.class,
                () -> service.sdk(1L, "abc", null));
    }
}
