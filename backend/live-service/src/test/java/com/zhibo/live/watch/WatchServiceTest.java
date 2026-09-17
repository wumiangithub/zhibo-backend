package com.zhibo.live.watch;

import com.zhibo.live.watch.dto.WatchResponse;
import com.zhibo.vhall.client.VhallClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WatchServiceTest {

    @Mock
    private VhallClient vhallClient;

    @InjectMocks
    private WatchService watchService;

    @Test
    void watch_appendsIdentityToEmbedUrl() {
        when(vhallClient.postForData(eq(WatchService.PATH_INFO), anyMap(), eq(Map.class)))
                .thenReturn(Map.of(
                        "subject", "直播课",
                        "webinar_state", 1,
                        "webinar_type", 2,
                        "full_embed_share_link", "https://live.example/embed/55"
                ));

        WatchResponse response = watchService.watch(55L, "guestABC", "小明");
        assertEquals(55L, response.getId());
        assertEquals("直播课", response.getTitle());
        assertEquals(1, response.getState());
        assertEquals(2, response.getType());
        assertEquals("guestABC", response.getGuestId());
        assertEquals("小明", response.getNickname());
        assertTrue(response.getEmbedUrl().startsWith("https://live.example/embed/55?"));
        assertTrue(response.getEmbedUrl().contains("email=guestABC"));
        assertTrue(response.getEmbedUrl().contains("zhibo.local"));
        assertTrue(response.getEmbedUrl().contains("nickname="));
        // 中文昵称应被 URL 编码，不能明文落在 query 里
        assertTrue(!response.getEmbedUrl().contains("小明"));
    }

    @Test
    void watch_generatesGuestIdAndDefaultNickname() {
        when(vhallClient.postForData(eq(WatchService.PATH_INFO), anyMap(), eq(Map.class)))
                .thenReturn(Map.of(
                        "subject", "课",
                        "full_embed_share_link", "https://live.example/embed/1"
                ));

        WatchResponse response = watchService.watch(1L, null, null);
        assertEquals(WatchService.DEFAULT_NICKNAME, response.getNickname());
        assertNotNull(response.getGuestId());
        assertTrue(response.getGuestId().matches("^[A-Za-z0-9]+$"));
        assertTrue(response.getEmbedUrl().contains("email=" + response.getGuestId()));
        assertTrue(response.getEmbedUrl().contains("zhibo.local"));
    }

    @Test
    void watch_rejectsInvalidGuestId() {
        assertThrows(IllegalArgumentException.class,
                () -> watchService.watch(1L, "bad-id", null));
    }

    @Test
    void watch_requiresFullEmbedShareLink() {
        when(vhallClient.postForData(eq(WatchService.PATH_INFO), anyMap(), eq(Map.class)))
                .thenReturn(Map.of("subject", "无嵌入"));

        assertThrows(IllegalStateException.class,
                () -> watchService.watch(1L, "abc", null));
    }
}
