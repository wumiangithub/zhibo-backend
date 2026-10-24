package com.zhibo.live.watch;

import com.zhibo.activity.ActivityEntity;
import com.zhibo.live.watch.dto.ChatFilterResponse;
import com.zhibo.liveprovider.LiveProvider;
import com.zhibo.liveprovider.LiveProviderRegistry;
import com.zhibo.liveprovider.ProviderNotAvailableException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChatFilterServiceTest {

    private static final String ID = "a1b2c3d4e5f67890abcdef1234567890";

    @Mock
    private WatchService watchService;
    @Mock
    private LiveProviderRegistry providerRegistry;
    @Mock
    private LiveProvider liveProvider;

    @InjectMocks
    private ChatFilterService chatFilterService;

    @Test
    void chatFilter_returnsPageUrl() {
        ActivityEntity entity = new ActivityEntity();
        entity.setId(ID);
        entity.setProvider("vhall");
        entity.setProviderRoomId("1001");

        when(watchService.requireActivity(ID)).thenReturn(entity);
        when(providerRegistry.require("vhall")).thenReturn(liveProvider);
        when(liveProvider.buildChatFilterUrl("1001", null, null))
                .thenReturn("https://e.vhall.com/v3/lives/authchat/1001?live_token=t");

        ChatFilterResponse response = chatFilterService.chatFilter(ID, null, null);
        assertEquals("https://e.vhall.com/v3/lives/authchat/1001?live_token=t", response.getPageUrl());
        verify(liveProvider).buildChatFilterUrl("1001", null, null);
    }

    @Test
    void chatFilter_unknownProvider_throws501() {
        ActivityEntity entity = new ActivityEntity();
        entity.setId(ID);
        entity.setProvider("aliyun");
        entity.setProviderRoomId("room-1");

        when(watchService.requireActivity(ID)).thenReturn(entity);
        when(providerRegistry.require("aliyun")).thenThrow(new ProviderNotAvailableException());

        assertThrows(ProviderNotAvailableException.class, () ->
                chatFilterService.chatFilter(ID, "a@b.c", "助理"));
    }
}