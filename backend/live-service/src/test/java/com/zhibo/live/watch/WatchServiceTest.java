package com.zhibo.live.watch;

import com.zhibo.activity.ActivityEntity;
import com.zhibo.activity.ActivityProductEntity;
import com.zhibo.activity.ActivityProductId;
import com.zhibo.activity.ActivityProductRepository;
import com.zhibo.activity.ActivityRecordEntity;
import com.zhibo.activity.ActivityRecordRepository;
import com.zhibo.activity.ActivityRepository;
import com.zhibo.live.watch.dto.WatchProductListResponse;
import com.zhibo.live.watch.dto.WatchRecordListResponse;
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
    private ActivityProductRepository productRepository;
    @Mock
    private ActivityRecordRepository activityRecordRepository;
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

        WatchResponse response = watchService.watch(ID, "guest1", "小明", null);
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
                () -> watchService.watch(ID, "guest1", "小明", null));
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

    @Test
    void listProducts_returnsBoundSnapshots() {
        ActivityEntity entity = new ActivityEntity();
        entity.setId(ID);
        entity.setReady(1);
        when(activityRepository.findById(ID)).thenReturn(Optional.of(entity));

        ActivityProductEntity row = new ActivityProductEntity();
        row.setId(new ActivityProductId(ID, "20002"));
        row.setProductName("满盈单化特训班");
        row.setSortOrder(0);
        when(productRepository.findByActivityIds(List.of(ID))).thenReturn(List.of(row));

        WatchProductListResponse response = watchService.listProducts(ID);
        assertEquals(1, response.getList().size());
        assertEquals("20002", response.getList().get(0).getSpuId());
        assertEquals("满盈单化特训班", response.getList().get(0).getProductName());
    }

    @Test
    void listRecords_mapsReadyLocalWithTos() {
        ActivityEntity entity = new ActivityEntity();
        entity.setId(ID);
        entity.setProvider("vhall");
        entity.setProviderRoomId("1001");
        entity.setReady(1);
        when(activityRepository.findById(ID)).thenReturn(Optional.of(entity));

        ActivityRecordEntity ready = new ActivityRecordEntity();
        ready.setRecordId(799703L);
        ready.setTitle("回放A");
        ready.setDurationSec(3723);
        ready.setStatus(ActivityRecordEntity.STATUS_READY);
        ready.setTosUrl("https://cdn.example.com/a.mp4");
        ready.setProviderCreatedTime(LocalDateTime.of(2026, 10, 29, 18, 0));
        ready.setCoverUrl("https://cover");

        ActivityRecordEntity pending = new ActivityRecordEntity();
        pending.setRecordId(1L);
        pending.setStatus(ActivityRecordEntity.STATUS_PACKAGING);
        pending.setTosUrl(null);

        when(activityRecordRepository.findByActivityId(ID)).thenReturn(List.of(ready, pending));

        WatchRecordListResponse response = watchService.listRecords(ID);
        assertEquals(1, response.getList().size());
        assertEquals(799703L, response.getList().get(0).getId());
        assertEquals("回放A", response.getList().get(0).getTitle());
        assertEquals("01:02:03", response.getList().get(0).getDuration());
        assertEquals("https://cdn.example.com/a.mp4", response.getList().get(0).getTosUrl());
        assertEquals("2026-10-29 18:00:00", response.getList().get(0).getCreatedTime());
        verifyNoInteractions(providerRegistry, liveProvider);
    }

    @Test
    void listRecords_noReady_returnsEmpty() {
        ActivityEntity entity = new ActivityEntity();
        entity.setId(ID);
        entity.setProvider("vhall");
        entity.setReady(1);
        when(activityRepository.findById(ID)).thenReturn(Optional.of(entity));
        when(activityRecordRepository.findByActivityId(ID)).thenReturn(List.of());

        WatchRecordListResponse response = watchService.listRecords(ID);
        assertTrue(response.getList().isEmpty());
        verifyNoInteractions(providerRegistry, liveProvider);
    }
}
