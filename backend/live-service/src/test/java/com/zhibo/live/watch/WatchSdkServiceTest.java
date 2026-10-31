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
import static org.junit.jupiter.api.Assertions.assertThrows;
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
    void sdk_mapsBundle_leanSignOnly() {
        ActivityEntity entity = new ActivityEntity();
        entity.setId(ID);
        entity.setProvider("vhall");
        entity.setProviderRoomId("1001");
        entity.setTitle("直播课");
        entity.setType(2);
        entity.setState(1);

        WatchSdkBundle.SdkInit sdk = new WatchSdkBundle.SdkInit();
        sdk.setAccount("12");
        sdk.setEmail("12@qkzb.com");
        sdk.setUsername("用户8079");
        sdk.setWebinarId("1001");
        sdk.setAppKey("key");
        sdk.setSignedAt("1");
        sdk.setSignType(0);
        sdk.setSign("abc");

        WatchSdkBundle bundle = new WatchSdkBundle();
        bundle.setSdk(sdk);

        when(watchService.requireActivity(ID)).thenReturn(entity);
        when(providerRegistry.require("vhall")).thenReturn(liveProvider);
        when(liveProvider.buildWatchSdk(any(WatchSdkCommand.class))).thenReturn(bundle);

        WatchSdkResponse response = watchSdkService.sdk(ID, "用户8079", 12L);
        assertEquals(ID, response.getId());
        assertEquals(12L, response.getViewerId());
        assertEquals("vhall", response.getProvider());
        assertEquals("用户8079", response.getNickname());
        assertEquals("1001", response.getSdk().getWebinarId());
        assertEquals("abc", response.getSdk().getSign());
        assertEquals("12@qkzb.com", response.getSdk().getEmail());
    }

    @Test
    void sdk_requiresViewerId() {
        assertThrows(IllegalArgumentException.class, () -> watchSdkService.sdk(ID, null, null));
        assertThrows(IllegalArgumentException.class, () -> watchSdkService.sdk(ID, null, 0L));
    }

    @Test
    void resolveSdkNickname_prefersClientWhenViewerId() {
        assertEquals("用户8079", WatchSdkService.resolveSdkNickname("用户8079", 12L));
        assertEquals("观众", WatchSdkService.resolveSdkNickname(null, 12L));
        assertEquals("观众", WatchSdkService.resolveSdkNickname("  ", 12L));
        assertEquals("本地昵称", WatchSdkService.resolveSdkNickname("本地昵称", null));
    }
}
