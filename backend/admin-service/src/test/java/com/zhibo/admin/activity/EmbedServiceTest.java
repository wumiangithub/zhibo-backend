package com.zhibo.admin.activity;

import com.zhibo.admin.activity.dto.ActivityEmbedResponse;
import com.zhibo.vhall.client.VhallClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EmbedServiceTest {

    @Mock
    private VhallClient vhallClient;

    @InjectMocks
    private EmbedService embedService;

    @Test
    void embed_buildsAdminConsoleUrl() {
        when(vhallClient.postForData(eq(EmbedService.PATH_GET_TOKEN), anyMap(), eq(Map.class)))
                .thenReturn(Map.of("token", "tok-abc"));

        ActivityEmbedResponse response = embedService.embed(555820923L);
        assertEquals(
                "https://e.vhall.com/v3/embed/live/detail/555820923?token=tok-abc",
                response.getEmbedUrl());
    }

    @Test
    void embed_requiresToken() {
        when(vhallClient.postForData(eq(EmbedService.PATH_GET_TOKEN), anyMap(), eq(Map.class)))
                .thenReturn(Map.of());

        assertThrows(IllegalStateException.class, () -> embedService.embed(1L));
    }

    @Test
    void embed_rejectsInvalidId() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> embedService.embed(0L));
        assertTrue(ex.getMessage().contains("活动 id"));
    }
}
