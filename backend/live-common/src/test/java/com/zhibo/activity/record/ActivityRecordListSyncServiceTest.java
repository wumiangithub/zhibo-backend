package com.zhibo.activity.record;

import com.zhibo.activity.ActivityEntity;
import com.zhibo.activity.ActivityRecordEntity;
import com.zhibo.activity.ActivityRecordRepository;
import com.zhibo.liveprovider.LiveProvider;
import com.zhibo.liveprovider.LiveProviderRegistry;
import com.zhibo.liveprovider.dto.ProviderRecordDownload;
import com.zhibo.liveprovider.dto.ProviderRecordItem;
import com.zhibo.liveprovider.dto.ProviderRecordList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ActivityRecordListSyncServiceTest {

    private static final String ACTIVITY_ID = "a1b2c3d4e5f67890abcdef1234567890";

    @Mock
    private ActivityRecordRepository activityRecordRepository;
    @Mock
    private LiveProviderRegistry providerRegistry;
    @Mock
    private ActivityRecordTosIngestService activityRecordTosIngestService;
    @Mock
    private LiveProvider liveProvider;

    private ActivityRecordListSyncService service;

    @BeforeEach
    void setUp() {
        service = new ActivityRecordListSyncService(
                activityRecordRepository, providerRegistry, activityRecordTosIngestService);
        org.mockito.Mockito.lenient()
                .when(activityRecordTosIngestService.tryMarkInFlight(anyLong()))
                .thenReturn(true);
    }

    @Test
    void sync_missingLocal_insertsPendingThenAdvancesViaCheck() {
        ActivityEntity activity = activity("vhall", "666781572");
        when(activityRecordRepository.findByActivityId(ACTIVITY_ID)).thenReturn(List.of());
        when(providerRegistry.require("vhall")).thenReturn(liveProvider);
        when(liveProvider.listRecords("666781572")).thenReturn(new ProviderRecordList(
                1L,
                List.of(new ProviderRecordItem(
                        799703L, "回放A", "01:02:03", "2026-10-29 18:00:00",
                        "https://cover", "1024", null))));
        when(activityRecordRepository.save(any(ActivityRecordEntity.class))).thenAnswer(inv -> {
            ActivityRecordEntity row = inv.getArgument(0);
            if (row.getId() == null) {
                row.setId(1L);
            }
            return row;
        });
        when(liveProvider.checkRecordDownload(799703L))
                .thenReturn(new ProviderRecordDownload(799703L, null, 0, 0));

        ActivityRecordListSyncService.SyncResult result = service.syncForActivity(activity);

        assertTrue(result.providerListOk());
        assertEquals(1, result.localByRecordId().size());
        ActivityRecordEntity synced = result.localByRecordId().get(799703L);
        assertEquals(799703L, synced.getRecordId());
        // 插 pending 后 check pack=0 → submit → packaging（同一实体被推进）
        assertEquals(ActivityRecordEntity.STATUS_PACKAGING, synced.getStatus());
        verify(liveProvider).submitRecordDownload(799703L);
        verify(activityRecordRepository, org.mockito.Mockito.atLeast(2)).save(any(ActivityRecordEntity.class));
    }

    @Test
    void sync_getListFails_stillAdvancesLocalStuck() {
        ActivityEntity activity = activity("vhall", "666781572");
        ActivityRecordEntity pending = new ActivityRecordEntity();
        pending.setRecordId(10L);
        pending.setStatus(ActivityRecordEntity.STATUS_PENDING_PACKAGE);
        when(activityRecordRepository.findByActivityId(ACTIVITY_ID)).thenReturn(List.of(pending));
        when(providerRegistry.require("vhall")).thenReturn(liveProvider);
        when(liveProvider.listRecords("666781572")).thenThrow(new RuntimeException("timeout"));
        when(liveProvider.checkRecordDownload(10L))
                .thenReturn(new ProviderRecordDownload(10L, null, 2, 50));

        ActivityRecordListSyncService.SyncResult result = service.syncForActivity(activity);

        assertFalse(result.providerListOk());
        assertEquals(1, result.localByRecordId().size());
        verify(liveProvider).checkRecordDownload(10L);
        verify(activityRecordRepository).save(pending);
        assertEquals(ActivityRecordEntity.STATUS_PACKAGING, pending.getStatus());
    }

    @Test
    void sync_noRoomId_returnsLocalOnly() {
        ActivityEntity activity = activity("vhall", null);
        when(activityRecordRepository.findByActivityId(ACTIVITY_ID)).thenReturn(List.of());

        ActivityRecordListSyncService.SyncResult result = service.syncForActivity(activity);

        assertFalse(result.providerListOk());
        assertTrue(result.localByRecordId().isEmpty());
        verify(providerRegistry, never()).require(any());
    }

    @Test
    void sync_existingZeroDuration_backfillsPositiveFromGetList() {
        ActivityEntity activity = activity("vhall", "666781572");
        ActivityRecordEntity ready = new ActivityRecordEntity();
        ready.setId(9L);
        ready.setRecordId(799703L);
        ready.setStatus(ActivityRecordEntity.STATUS_READY);
        ready.setDurationSec(0);
        ready.setTosUrl("https://tos/x.mp4");
        when(activityRecordRepository.findByActivityId(ACTIVITY_ID)).thenReturn(List.of(ready));
        when(providerRegistry.require("vhall")).thenReturn(liveProvider);
        when(liveProvider.listRecords("666781572")).thenReturn(new ProviderRecordList(
                1L,
                List.of(new ProviderRecordItem(
                        799703L, "回放A", "00:10:18", "2026-11-05 17:33:18",
                        "https://cover", "1024", null))));
        when(activityRecordRepository.save(any(ActivityRecordEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        ActivityRecordListSyncService.SyncResult result = service.syncForActivity(activity);

        assertTrue(result.providerListOk());
        assertEquals(618, ready.getDurationSec());
        verify(activityRecordRepository).save(ready);
        // ready 不需要 check 推进
        verify(liveProvider, never()).checkRecordDownload(anyLong());
    }

    @Test
    void sync_existingPositiveDuration_doesNotOverwrite() {
        ActivityEntity activity = activity("vhall", "666781572");
        ActivityRecordEntity ready = new ActivityRecordEntity();
        ready.setId(9L);
        ready.setRecordId(799703L);
        ready.setStatus(ActivityRecordEntity.STATUS_READY);
        ready.setDurationSec(618);
        when(activityRecordRepository.findByActivityId(ACTIVITY_ID)).thenReturn(List.of(ready));
        when(providerRegistry.require("vhall")).thenReturn(liveProvider);
        when(liveProvider.listRecords("666781572")).thenReturn(new ProviderRecordList(
                1L,
                List.of(new ProviderRecordItem(
                        799703L, "回放A", "01:00:00", "2026-11-05 17:33:18",
                        null, null, null))));

        service.syncForActivity(activity);

        assertEquals(618, ready.getDurationSec());
        verify(activityRecordRepository, never()).save(any());
    }

    @Test
    void isStuckDownloading_trueWhenNoTosAndNotInFlight() {
        ActivityRecordEntity row = new ActivityRecordEntity();
        row.setRecordId(1L);
        row.setStatus(ActivityRecordEntity.STATUS_DOWNLOADING);
        row.setTosUrl(null);
        when(activityRecordTosIngestService.isIngestInFlight(1L)).thenReturn(false);
        assertTrue(service.isStuckDownloading(row));
    }

    @Test
    void isStuckDownloading_falseWhenInFlight() {
        ActivityRecordEntity row = new ActivityRecordEntity();
        row.setRecordId(1L);
        row.setStatus(ActivityRecordEntity.STATUS_DOWNLOADING);
        when(activityRecordTosIngestService.isIngestInFlight(1L)).thenReturn(true);
        assertFalse(service.isStuckDownloading(row));
    }

    private static ActivityEntity activity(String provider, String roomId) {
        ActivityEntity e = new ActivityEntity();
        e.setId(ACTIVITY_ID);
        e.setProvider(provider);
        e.setProviderRoomId(roomId);
        return e;
    }
}
