package com.zhibo.activity.session;

import com.zhibo.activity.ActivityEntity;
import com.zhibo.activity.ActivityLiveSessionEntity;
import com.zhibo.activity.ActivityLiveSessionRepository;
import com.zhibo.activity.ActivityRepository;
import com.zhibo.liveprovider.LiveProvider;
import com.zhibo.liveprovider.LiveProviderRegistry;
import com.zhibo.liveprovider.dto.ProviderWebinarSwitchItem;
import com.zhibo.liveprovider.dto.ProviderWebinarSwitchList;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ActivityLiveSessionSyncServiceTest {

    private static final String ACTIVITY_ID = "a1b2c3d4e5f67890abcdef1234567890";

    @Mock
    private ActivityLiveSessionRepository sessionRepository;
    @Mock
    private ActivityRepository activityRepository;
    @Mock
    private LiveProviderRegistry providerRegistry;
    @Mock
    private LiveProvider liveProvider;

    private ActivityLiveSessionSyncService service;
    private final AtomicLong idSeq = new AtomicLong(1);
    private final List<ActivityLiveSessionEntity> store = new ArrayList<>();

    @BeforeEach
    void setUp() {
        service = new ActivityLiveSessionSyncService(sessionRepository, activityRepository, providerRegistry);
        org.mockito.Mockito.lenient().when(providerRegistry.require("vhall")).thenReturn(liveProvider);
        org.mockito.Mockito.lenient().when(sessionRepository.findByActivityId(ACTIVITY_ID))
                .thenAnswer(inv -> List.copyOf(store));
        org.mockito.Mockito.lenient()
                .when(sessionRepository.findByActivityIdAndProviderAndSwitchId(eq(ACTIVITY_ID), eq("vhall"), any()))
                .thenAnswer(inv -> {
                    Long switchId = inv.getArgument(2);
                    return store.stream()
                            .filter(r -> switchId.equals(r.getSwitchId()))
                            .findFirst();
                });
        org.mockito.Mockito.lenient().when(sessionRepository.save(any())).thenAnswer(inv -> {
            ActivityLiveSessionEntity row = inv.getArgument(0);
            if (row.getId() == null) {
                row.setId(idSeq.getAndIncrement());
                store.add(row);
            } else {
                store.removeIf(r -> r.getId().equals(row.getId()));
                store.add(row);
            }
            return row;
        });
        org.mockito.Mockito.lenient().doAnswer(inv -> {
            ActivityLiveSessionEntity row = inv.getArgument(0);
            store.removeIf(r -> r.getId() != null && r.getId().equals(row.getId()));
            return null;
        }).when(sessionRepository).delete(any());
    }

    @Test
    void sync_upsertsBySwitchIdAndRewritesActualTimes() {
        ActivityEntity activity = activity();
        ActivityLiveSessionEntity provisional = new ActivityLiveSessionEntity();
        provisional.setId(99L);
        provisional.setActivityId(ACTIVITY_ID);
        provisional.setProvider("vhall");
        provisional.setSwitchId(ActivityLiveSessionEntity.PROVISIONAL_SWITCH_ID);
        provisional.setStartTime(LocalDateTime.of(2026, 11, 1, 10, 0));
        store.add(provisional);

        when(liveProvider.listWebinarSwitches("1001")).thenReturn(new ProviderWebinarSwitchList(
                "2026-11-01 09:00:00",
                null,
                "2026-11-01 10:00:00",
                "2026-11-01 16:00:00",
                7200,
                List.of(
                        new ProviderWebinarSwitchItem(
                                11L,
                                "2026-11-01 10:00:00",
                                "2026-11-01 11:00:00",
                                0, 4, 4),
                        new ProviderWebinarSwitchItem(
                                12L,
                                "2026-11-01 14:00:00",
                                "2026-11-01 16:00:00",
                                0, 4, 4))));

        List<ActivityLiveSessionEntity> rows = service.syncForActivity(activity);

        assertEquals(2, rows.size());
        assertEquals(LocalDateTime.of(2026, 11, 1, 10, 0), activity.getLiveActualStartTime());
        assertEquals(LocalDateTime.of(2026, 11, 1, 16, 0), activity.getLiveActualEndTime());
        verify(activityRepository).save(activity);
        verify(sessionRepository).delete(provisional);

        // 再 sync 覆盖末结
        activity.setLiveActualEndTime(LocalDateTime.of(2026, 11, 1, 15, 0));
        when(liveProvider.listWebinarSwitches("1001")).thenReturn(new ProviderWebinarSwitchList(
                null, null, null, null, null,
                List.of(
                        new ProviderWebinarSwitchItem(
                                11L, "2026-11-01 10:00:00", "2026-11-01 11:00:00", 0, 4, 4),
                        new ProviderWebinarSwitchItem(
                                12L, "2026-11-01 14:00:00", "2026-11-01 17:00:00", 0, 4, 4))));
        service.syncForActivity(activity);
        assertEquals(LocalDateTime.of(2026, 11, 1, 17, 0), activity.getLiveActualEndTime());
    }

    @Test
    void syncQuietly_returnsLocalOnProviderFailure() {
        ActivityEntity activity = activity();
        when(liveProvider.listWebinarSwitches("1001")).thenThrow(new RuntimeException("timeout"));
        ActivityLiveSessionEntity existing = new ActivityLiveSessionEntity();
        existing.setId(1L);
        existing.setActivityId(ACTIVITY_ID);
        existing.setProvider("vhall");
        existing.setSwitchId(5L);
        store.add(existing);

        List<ActivityLiveSessionEntity> rows = service.syncQuietly(activity);
        assertEquals(1, rows.size());
        assertEquals(5L, rows.get(0).getSwitchId());
        assertNull(activity.getLiveActualStartTime());
    }

    private static ActivityEntity activity() {
        ActivityEntity e = new ActivityEntity();
        e.setId(ACTIVITY_ID);
        e.setProvider("vhall");
        e.setProviderRoomId("1001");
        e.setState(3);
        return e;
    }
}
