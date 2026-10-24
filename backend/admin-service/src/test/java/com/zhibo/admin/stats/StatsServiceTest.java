package com.zhibo.admin.stats;

import com.zhibo.activity.ActivityEntity;
import com.zhibo.admin.activity.ActivityService;
import com.zhibo.admin.stats.dto.ActivityStatsResponse;
import com.zhibo.admin.stats.dto.OnlineTrendResponse;
import com.zhibo.liveprovider.LiveProvider;
import com.zhibo.liveprovider.LiveProviderRegistry;
import com.zhibo.liveprovider.dto.OnlinePoint;
import com.zhibo.liveprovider.dto.ProviderRoomSnapshot;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StatsServiceTest {

    private static final String ID = "a1b2c3d4-e5f6-7890-abcd-ef1234567890";

    @Mock
    private ActivityService activityService;
    @Mock
    private LiveProviderRegistry providerRegistry;
    @Mock
    private LiveProvider liveProvider;

    private final AtomicReference<Instant> now =
            new AtomicReference<>(Instant.parse("2026-09-18T10:00:00Z"));

    private StatsService statsService;
    private ActivityEntity entity;

    @BeforeEach
    void setUp() {
        Clock clock = new Clock() {
            @Override
            public ZoneOffset getZone() {
                return ZoneOffset.UTC;
            }

            @Override
            public Clock withZone(java.time.ZoneId zone) {
                return this;
            }

            @Override
            public Instant instant() {
                return now.get();
            }
        };
        statsService = new StatsService(activityService, providerRegistry, clock);

        entity = new ActivityEntity();
        entity.setId(ID);
        entity.setProvider("vhall");
        entity.setProviderRoomId("1001");
        entity.setTitle("课");
        entity.setType(2);
        entity.setState(1);
    }

    @Test
    void summary_combinesInfoAndOnline() {
        when(activityService.requireActivity(ID)).thenReturn(entity);
        when(providerRegistry.require("vhall")).thenReturn(liveProvider);
        when(liveProvider.fetchRoom("1001")).thenReturn(new ProviderRoomSnapshot(
                "课", 1, 2, 88, null, null, null, null, null, null));
        when(liveProvider.currentOnline("1001")).thenReturn(12);

        ActivityStatsResponse response = statsService.summary(ID);
        assertEquals(ID, response.getId());
        assertEquals("课", response.getTitle());
        assertEquals(1, response.getState());
        assertEquals(2, response.getType());
        assertEquals(88, response.getPv());
        assertEquals(12, response.getOnlineCount());
    }

    @Test
    void online_cachesSixtySeconds() {
        when(activityService.requireActivity(ID)).thenReturn(entity);
        when(providerRegistry.require("vhall")).thenReturn(liveProvider);
        when(liveProvider.fetchRoom("1001")).thenReturn(new ProviderRoomSnapshot(
                "课", 1, 2, 1, null, null, null, null, null, null));
        when(liveProvider.currentOnline("1001")).thenReturn(3);

        statsService.summary(ID);
        now.set(now.get().plus(Duration.ofSeconds(30)));
        statsService.summary(ID);
        verify(liveProvider, times(1)).currentOnline("1001");
    }

    @Test
    void trend_rejectsSpanOverOneDay() {
        when(activityService.requireActivity(ID)).thenReturn(entity);
        assertThrows(IllegalArgumentException.class, () ->
                statsService.onlineTrend(ID, "2026-09-17 10:00:00", "2026-09-18 10:00:01"));
    }

    @Test
    void trend_mapsPoints() {
        when(activityService.requireActivity(ID)).thenReturn(entity);
        when(providerRegistry.require("vhall")).thenReturn(liveProvider);
        when(liveProvider.onlineTrend("1001", "2026-09-17 10:00:00", "2026-09-17 12:00:00"))
                .thenReturn(List.of(new OnlinePoint("2026-09-17 10:00:00", 3)));

        OnlineTrendResponse response = statsService.onlineTrend(
                ID, "2026-09-17 10:00:00", "2026-09-17 12:00:00");
        assertEquals(ID, response.getId());
        assertEquals(1, response.getPoints().size());
        assertEquals(3, response.getPoints().get(0).getCount());
    }
}
