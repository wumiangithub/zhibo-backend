package com.zhibo.admin.activity;

import com.zhibo.admin.activity.dto.ActivityHostResponse;
import com.zhibo.vhall.client.VhallClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HostServiceTest {

    @Mock
    private VhallClient vhallClient;

    @InjectMocks
    private HostService hostService;

    @Test
    void host_returnsPageUrl() {
        when(vhallClient.getForData(eq(HostService.PATH_ROLE_URL), anyMap(), eq(Map.class)))
                .thenReturn(Map.of("page_url", "https://host.example/page"));

        ActivityHostResponse response = hostService.host(100L);
        assertEquals("https://host.example/page", response.getHostUrl());
    }

    @Test
    void host_requiresPageUrl() {
        when(vhallClient.getForData(eq(HostService.PATH_ROLE_URL), anyMap(), eq(Map.class)))
                .thenReturn(Map.of());

        assertThrows(IllegalStateException.class, () -> hostService.host(100L));
    }
}
