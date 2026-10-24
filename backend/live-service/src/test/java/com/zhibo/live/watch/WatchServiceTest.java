package com.zhibo.live.watch;

import com.zhibo.activity.ActivityEntity;
import com.zhibo.activity.ActivityRepository;
import com.zhibo.live.watch.dto.WatchResponse;
import com.zhibo.liveprovider.LiveProvider;
import com.zhibo.liveprovider.LiveProviderRegistry;
import com.zhibo.liveprovider.dto.ProviderRoomSnapshot;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WatchServiceTest {

    private static final String ID = "a1b2c3d4e5f67890abcdef1234567890";

    @Mock
    private ActivityRepository activityRepository;
    @Mock
    private LiveProviderRegistry providerRegistry;
    @Mock
    private LiveProvider liveProvider;

    @InjectMocks
    private WatchService watchService;

    @Test
    void watch_buildsEmbedUrl() {
        ActivityEntity entity = new ActivityEntity();
        entity.setId(ID);
        entity.setProvider("vhall");
        entity.setProviderRoomId("1001");
        entity.setTitle("直播课");
        entity.setType(2);
        entity.setState(1);
        entity.setStartTime("2026-09-18 20:00:00");

        when(activityRepository.findById(ID)).thenReturn(Optional.of(entity));
        when(providerRegistry.require("vhall")).thenReturn(liveProvider);
        when(liveProvider.fetchRoom("1001")).thenReturn(new ProviderRoomSnapshot(
                "直播课", 1, 2, null,
                "https://live.example.com/watch/1001",
                "2026-09-18 20:00:00", null, null, null, null));

        WatchResponse response = watchService.watch(ID, "guest1", "小明");
        assertEquals(ID, response.getId());
        assertEquals("vhall", response.getProvider());
        assertEquals("直播课", response.getTitle());
        assertTrue(response.getEmbedUrl().contains("email=guest1%40zhibo.local")
                || response.getEmbedUrl().contains("email=guest1@zhibo.local"));
        assertEquals("guest1", response.getGuestId());
        assertEquals("小明", response.getNickname());
    }

    @Test
    void listActivities_rejectsPageBelowOne() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> watchService.listActivities(0, 20, null));
        assertEquals("page 不能小于 1", ex.getMessage());
    }

    @Test
    void watch_softDeleted_isMissing() {
        ActivityEntity entity = new ActivityEntity();
        entity.setId(ID);
        entity.setDeletedTime(LocalDateTime.now());
        when(activityRepository.findById(ID)).thenReturn(Optional.of(entity));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> watchService.watch(ID, "guest1", "小明"));
        assertEquals("活动不存在", ex.getMessage());
        verifyNoInteractions(providerRegistry, liveProvider);
    }

    @Test
    void listActivities_hidesSoftDeleted() {
        ActivityEntity dead = new ActivityEntity();
        dead.setId(ID);
        dead.setTitle("已删");
        dead.setDeletedTime(LocalDateTime.now());
        when(activityRepository.search(any(), anyBoolean(), any(), any(), any(), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of(dead)));

        var response = watchService.listActivities(1, 20, null);
        assertEquals(0, response.getList().size());
    }
}
