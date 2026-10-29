package com.zhibo.live.watch;

import com.zhibo.activity.ActivityEntity;
import com.zhibo.live.watch.dto.WatchSdkResponse;
import com.zhibo.liveprovider.LiveProvider;
import com.zhibo.liveprovider.LiveProviderRegistry;
import com.zhibo.liveprovider.dto.WatchSdkBundle;
import com.zhibo.liveprovider.dto.WatchSdkCommand;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WatchSdkServiceTest {

    private static final String ID = "a1b2c3d4e5f67890abcdef1234567890";

    @Mock
    private WatchService watchService;
    @Mock
    private LiveProviderRegistry providerRegistry;
    @Mock
    private LiveProvider liveProvider;

    @InjectMocks
    private WatchSdkService watchSdkService;

    @Test
    void sdk_mapsBundle() {
        ActivityEntity entity = new ActivityEntity();
        entity.setId(ID);
        entity.setProvider("vhall");
        entity.setProviderRoomId("1001");
        entity.setTitle("直播课");
        entity.setType(2);
        entity.setState(1);
        entity.setStartTime("2026-09-18 20:00:00");
        entity.setPlaybackEnabled(Boolean.TRUE);

        WatchSdkBundle.SdkInit sdk = new WatchSdkBundle.SdkInit();
        sdk.setScriptUrl("https://cdn/VhallSDK.js");
        sdk.setAccount("guest1");
        sdk.setEmail("guest1@zhibo.local");
        sdk.setUsername("观众");
        sdk.setWebinarId("1001");
        sdk.setAppKey("key");
        sdk.setSignedAt("1");
        sdk.setSignType(0);
        sdk.setSign("abc");

        WatchSdkBundle bundle = new WatchSdkBundle();
        bundle.setTitle("直播课");
        bundle.setState(1);
        bundle.setType(2);
        bundle.setStartTime("2026-09-18 20:00:00");
        bundle.setEmbedUrl("https://embed");
        bundle.setSdk(sdk);

        when(watchService.requireActivity(ID)).thenReturn(entity);
        when(providerRegistry.require("vhall")).thenReturn(liveProvider);
        when(liveProvider.buildWatchSdk(any(WatchSdkCommand.class))).thenReturn(bundle);

        WatchSdkResponse response = watchSdkService.sdk(ID, "guest1", "用户8079", 12L);
        assertEquals(ID, response.getId());
        assertEquals("vhall", response.getProvider());
        assertEquals("1001", response.getSdk().getWebinarId());
        assertEquals("abc", response.getSdk().getSign());
        assertEquals(Boolean.TRUE, response.getPlaybackEnabled());
    }

    @Test
    void resolveSdkNickname_prefersClientWhenViewerId() {
        assertEquals("用户8079", WatchSdkService.resolveSdkNickname("用户8079", 12L));
        assertEquals("观众", WatchSdkService.resolveSdkNickname(null, 12L));
        assertEquals("观众", WatchSdkService.resolveSdkNickname("  ", 12L));
        assertEquals("本地昵称", WatchSdkService.resolveSdkNickname("本地昵称", null));
    }

    @Test
    void sdk_nullPlaybackEnabled_defaultsTrue() {
        ActivityEntity entity = new ActivityEntity();
        entity.setId(ID);
        entity.setProvider("vhall");
        entity.setProviderRoomId("1001");
        entity.setTitle("直播课");
        entity.setType(2);
        entity.setState(3);

        WatchSdkBundle.SdkInit sdk = new WatchSdkBundle.SdkInit();
        sdk.setScriptUrl("https://cdn/VhallSDK.js");
        sdk.setAccount("guest1");
        sdk.setEmail("guest1@zhibo.local");
        sdk.setUsername("观众");
        sdk.setWebinarId("1001");
        sdk.setAppKey("key");
        sdk.setSignedAt("1");
        sdk.setSign("abc");

        WatchSdkBundle bundle = new WatchSdkBundle();
        bundle.setState(3);
        bundle.setSdk(sdk);

        when(watchService.requireActivity(ID)).thenReturn(entity);
        when(providerRegistry.require("vhall")).thenReturn(liveProvider);
        when(liveProvider.buildWatchSdk(any(WatchSdkCommand.class))).thenReturn(bundle);

        WatchSdkResponse response = watchSdkService.sdk(ID, "guest1", null, null);
        assertEquals(Boolean.TRUE, response.getPlaybackEnabled());
    }
}
