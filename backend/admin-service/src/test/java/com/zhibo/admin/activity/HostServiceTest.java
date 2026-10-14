package com.zhibo.admin.activity;

import com.zhibo.activity.ActivityEntity;
import com.zhibo.admin.activity.dto.ActivityHostResponse;
import com.zhibo.liveprovider.LiveProvider;
import com.zhibo.liveprovider.LiveProviderRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HostServiceTest {

    @Mock
    private ActivityService activityService;
    @Mock
    private LiveProviderRegistry providerRegistry;
    @Mock
    private LiveProvider liveProvider;

    @InjectMocks
    private HostService hostService;

    @Test
    void host_returnsPageUrl() {
        ActivityEntity entity = new ActivityEntity();
        entity.setId("a1b2c3d4-e5f6-7890-abcd-ef1234567890");
        entity.setProvider("vhall");
        entity.setProviderRoomId("100");
        entity.setRiskStatus("passed");
        entity.setShelfStatus("on");
        entity.setState(2);
        when(activityService.requireActivity(entity.getId())).thenReturn(entity);
        when(providerRegistry.require("vhall")).thenReturn(liveProvider);
        when(liveProvider.buildHostUrl("100")).thenReturn("https://host.example/page");

        ActivityHostResponse response = hostService.host(entity.getId());
        assertEquals("vhall", response.getProvider());
        assertEquals("https://host.example/page", response.getHostUrl());
    }

    @Test
    void host_rejectsWhenCannotBroadcast() {
        ActivityEntity entity = new ActivityEntity();
        entity.setId("a1b2c3d4-e5f6-7890-abcd-ef1234567890");
        entity.setProvider("vhall");
        entity.setProviderRoomId("100");
        entity.setRiskStatus("pending");
        entity.setShelfStatus("on");
        entity.setState(2);
        when(activityService.requireActivity(entity.getId())).thenReturn(entity);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> hostService.host(entity.getId()));
        assertEquals("当前状态不可开播", ex.getMessage());
        verify(activityService).requireActivity(entity.getId());
        verify(providerRegistry, never()).require(eq("vhall"));
        verifyNoInteractions(liveProvider);
    }
}
