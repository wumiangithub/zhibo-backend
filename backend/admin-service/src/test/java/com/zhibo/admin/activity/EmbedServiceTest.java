package com.zhibo.admin.activity;

import com.zhibo.activity.ActivityEntity;
import com.zhibo.admin.activity.dto.ActivityEmbedResponse;
import com.zhibo.liveprovider.LiveProvider;
import com.zhibo.liveprovider.LiveProviderRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EmbedServiceTest {

    @Mock
    private ActivityService activityService;
    @Mock
    private LiveProviderRegistry providerRegistry;
    @Mock
    private LiveProvider liveProvider;

    @InjectMocks
    private EmbedService embedService;

    @Test
    void embed_returnsUrl() {
        ActivityEntity entity = new ActivityEntity();
        entity.setId("a1b2c3d4-e5f6-7890-abcd-ef1234567890");
        entity.setProvider("vhall");
        entity.setProviderRoomId("9");
        when(activityService.requireActivity(entity.getId())).thenReturn(entity);
        when(providerRegistry.require("vhall")).thenReturn(liveProvider);
        when(liveProvider.buildAdminEmbedUrl("9"))
                .thenReturn("https://e.vhall.com/v3/embed/live/detail/9?token=t");

        ActivityEmbedResponse response = embedService.embed(entity.getId());
        assertEquals("https://e.vhall.com/v3/embed/live/detail/9?token=t", response.getEmbedUrl());
    }
}
