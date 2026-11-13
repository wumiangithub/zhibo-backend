package com.zhibo.activity.cloud;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.zhibo.activity.ActivityEntity;
import com.zhibo.activity.ActivityLiveSessionEntity;
import com.zhibo.activity.ActivityLiveSessionRepository;
import com.zhibo.activity.ActivityRepository;
import com.zhibo.activity.cloud.ActivityCloudSyncService.Options;
import com.zhibo.activity.record.ActivityRecordListSyncService;
import com.zhibo.activity.session.ActivityLiveSessionSyncService;
import com.zhibo.liveprovider.LiveProvider;
import com.zhibo.liveprovider.LiveProviderRegistry;
import com.zhibo.liveprovider.dto.ProviderRoomSnapshot;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ActivityCloudSyncServiceTest {

    @Mock
    private ActivityRepository activityRepository;
    @Mock
    private ActivityLiveSessionRepository sessionRepository;
    @Mock
    private LiveProviderRegistry providerRegistry;
    @Mock
    private LiveProvider liveProvider;
    @Mock
    private ActivityLiveSessionSyncService sessionSyncService;
    @Mock
    private ActivityRecordListSyncService recordListSyncService;

    private ActivityCloudSyncService service;

    @BeforeEach
    void setUp() {
        service = new ActivityCloudSyncService(
                activityRepository,
                sessionRepository,
                providerRegistry,
                sessionSyncService,
                recordListSyncService);
    }

    @Test
    void syncQuietly_nonVhall_skipsAll() {
        ActivityEntity activity = activity("a1", "aliyun", "100");
        when(sessionRepository.findByActivityId("a1")).thenReturn(List.of());

        ActivityCloudSyncService.Result result = service.syncQuietly(activity, Options.explicitFull());

        assertFalse(result.applicable());
        verify(providerRegistry, never()).require(any());
        verify(sessionSyncService, never()).syncQuietly(any());
        verify(recordListSyncService, never()).syncForActivity(any());
    }

    @Test
    void syncQuietly_watchSummary_updatesStateAndRunsRecordsWhenEnded() {
        ActivityEntity activity = activity("a1", "vhall", "1001");
        activity.setState(2);
        when(providerRegistry.require("vhall")).thenReturn(liveProvider);
        when(liveProvider.fetchRoom("1001")).thenReturn(snap(3));
        when(sessionSyncService.syncQuietly(activity)).thenReturn(List.of(new ActivityLiveSessionEntity()));
        when(recordListSyncService.syncForActivity(activity))
                .thenReturn(new ActivityRecordListSyncService.SyncResult(Map.of(), List.of(), true));

        ActivityCloudSyncService.Result result = service.syncQuietly(activity, Options.watchSummary());

        assertTrue(result.applicable());
        assertTrue(result.stateUpdated());
        assertEquals(3, activity.getState());
        assertTrue(result.sessionsAttempted());
        assertTrue(result.recordsAttempted());
        assertTrue(result.recordsOk());
        verify(activityRepository).save(activity);
    }

    @Test
    void syncQuietly_consoleSummary_skipsRecordsEvenWhenEnded() {
        ActivityEntity activity = activity("a1", "vhall", "1001");
        activity.setState(3);
        when(providerRegistry.require("vhall")).thenReturn(liveProvider);
        when(liveProvider.fetchRoom("1001")).thenReturn(snap(3));
        when(sessionSyncService.syncQuietly(activity)).thenReturn(List.of());

        ActivityCloudSyncService.Result result = service.syncQuietly(activity, Options.consoleSummary());

        assertTrue(result.applicable());
        assertFalse(result.recordsAttempted());
        verify(recordListSyncService, never()).syncForActivity(any());
    }

    @Test
    void syncQuietly_preview_skipsRecords() {
        ActivityEntity activity = activity("a1", "vhall", "1001");
        activity.setState(2);
        when(providerRegistry.require("vhall")).thenReturn(liveProvider);
        when(liveProvider.fetchRoom("1001")).thenReturn(snap(2));
        when(sessionSyncService.syncQuietly(activity)).thenReturn(List.of());

        ActivityCloudSyncService.Result result = service.syncQuietly(activity, Options.watchSummary());

        assertFalse(result.recordsAttempted());
        verify(recordListSyncService, never()).syncForActivity(any());
    }

    @Test
    void syncQuietly_infoFailure_stillSyncsSessionsAndDoesNotThrow() {
        ActivityEntity activity = activity("a1", "vhall", "1001");
        activity.setState(3);
        when(providerRegistry.require("vhall")).thenReturn(liveProvider);
        when(liveProvider.fetchRoom("1001")).thenThrow(new RuntimeException("timeout"));
        when(sessionSyncService.syncQuietly(activity)).thenReturn(List.of());
        when(recordListSyncService.syncForActivity(activity))
                .thenReturn(new ActivityRecordListSyncService.SyncResult(Map.of(), List.of(), false));

        ActivityCloudSyncService.Result result = service.syncQuietly(activity, Options.watchSummary());

        assertTrue(result.applicable());
        assertTrue(result.stateAttempted());
        assertFalse(result.stateUpdated());
        assertTrue(result.sessionsAttempted());
        assertTrue(result.recordsAttempted());
    }

    @Test
    void syncQuietly_sessionsOnly_doesNotTouchInfoOrRecords() {
        ActivityEntity activity = activity("a1", "vhall", "1001");
        when(sessionSyncService.syncQuietly(activity)).thenReturn(List.of());

        ActivityCloudSyncService.Result result = service.syncQuietly(activity, Options.sessionsOnly());

        assertTrue(result.sessionsAttempted());
        assertFalse(result.stateAttempted());
        assertFalse(result.recordsAttempted());
        verify(providerRegistry, never()).require(any());
    }

    @Test
    void syncQuietly_actualEndWithoutEndedState_stillRunsRecordsOnExplicit() {
        ActivityEntity activity = activity("a1", "vhall", "1001");
        activity.setState(1);
        activity.setLiveActualEndTime(LocalDateTime.of(2026, 11, 1, 12, 0));
        when(providerRegistry.require("vhall")).thenReturn(liveProvider);
        when(liveProvider.fetchRoom("1001")).thenReturn(snap(1));
        when(sessionSyncService.syncQuietly(activity)).thenReturn(List.of());
        when(recordListSyncService.syncForActivity(activity))
                .thenReturn(new ActivityRecordListSyncService.SyncResult(Map.of(), List.of(), true));

        ActivityCloudSyncService.Result result = service.syncQuietly(activity, Options.explicitFull());

        assertTrue(result.recordsAttempted());
    }

    @Test
    void syncQuietlyById_missing_returnsNull() {
        when(activityRepository.findById("missing")).thenReturn(Optional.empty());
        assertEquals(null, service.syncQuietlyById("missing", Options.sessionsOnly()));
    }

    private static ProviderRoomSnapshot snap(Integer state) {
        return new ProviderRoomSnapshot("t", state, 2, null, null, null, null, null, null, null);
    }

    private static ActivityEntity activity(String id, String provider, String roomId) {
        ActivityEntity entity = new ActivityEntity();
        entity.setId(id);
        entity.setProvider(provider);
        entity.setProviderRoomId(roomId);
        return entity;
    }
}
