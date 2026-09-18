package com.zhibo.admin.stats;

import com.zhibo.admin.stats.dto.ActivityStatsResponse;
import com.zhibo.admin.stats.dto.OnlineTrendResponse;
import com.zhibo.vhall.client.VhallClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StatsServiceTest {

    @Mock
    private VhallClient vhallClient;

    private final AtomicReference<Instant> now =
            new AtomicReference<>(Instant.parse("2026-09-18T10:00:00Z"));

    private StatsService statsService;

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
        statsService = new StatsService(vhallClient, clock);
    }

    @Test
    void summary_combinesInfoAndOnline() {
        when(vhallClient.postForData(eq(StatsService.PATH_INFO), anyMap(), eq(Map.class)))
                .thenReturn(Map.of(
                        "subject", "课",
                        "webinar_state", 1,
                        "webinar_type", 2,
                        "pv", 88
                ));
        when(vhallClient.postForData(eq(StatsService.PATH_ONLINE_NOW), anyMap(), eq(Map.class)))
                .thenReturn(Map.of("num", 12));

        ActivityStatsResponse response = statsService.summary(1001L);
        assertEquals(1001L, response.getId());
        assertEquals("课", response.getTitle());
        assertEquals(1, response.getState());
        assertEquals(2, response.getType());
        assertEquals(88, response.getPv());
        assertEquals(12, response.getOnlineCount());
    }

    @Test
    void summary_reusesOnlineWithin60Seconds() {
        when(vhallClient.postForData(eq(StatsService.PATH_INFO), anyMap(), eq(Map.class)))
                .thenReturn(Map.of("subject", "课", "webinar_state", 1, "webinar_type", 2, "pv", 1));
        when(vhallClient.postForData(eq(StatsService.PATH_ONLINE_NOW), anyMap(), eq(Map.class)))
                .thenReturn(Map.of("num", 7), Map.of("num", 99));

        assertEquals(7, statsService.summary(42L).getOnlineCount());
        now.set(now.get().plus(Duration.ofSeconds(59)));
        assertEquals(7, statsService.summary(42L).getOnlineCount());
        verify(vhallClient, times(1))
                .postForData(eq(StatsService.PATH_ONLINE_NOW), anyMap(), eq(Map.class));

        now.set(now.get().plus(Duration.ofSeconds(1)));
        assertEquals(99, statsService.summary(42L).getOnlineCount());
        verify(vhallClient, times(2))
                .postForData(eq(StatsService.PATH_ONLINE_NOW), anyMap(), eq(Map.class));
    }

    @Test
    void onlineTrend_mapsPointsAndTruncatesToMinuteForVhall() {
        when(vhallClient.postForData(eq(StatsService.PATH_ONLINE_TREND), anyMap(), eq(Map.class)))
                .thenReturn(Map.of("list", List.of(
                        Map.of("time", "2026-09-17 10:00:00", "total", 3),
                        Map.of("time", "2026-09-17 10:01:00", "total", 5)
                )));

        OnlineTrendResponse response = statsService.onlineTrend(
                9L, "2026-09-17 10:00:00", "2026-09-17 11:00:00");
        assertEquals(9L, response.getId());
        assertEquals("2026-09-17 10:00:00", response.getStartTime());
        assertEquals("2026-09-17 11:00:00", response.getEndTime());
        assertEquals(2, response.getPoints().size());
        assertEquals("2026-09-17 10:01:00", response.getPoints().get(1).getTime());
        assertEquals(5, response.getPoints().get(1).getCount());

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
        verify(vhallClient).postForData(eq(StatsService.PATH_ONLINE_TREND), captor.capture(), eq(Map.class));
        assertEquals("2026-09-17 10:00", captor.getValue().get("start_time"));
        assertEquals("2026-09-17 11:00", captor.getValue().get("end_time"));
    }

    @Test
    void onlineTrend_rejectsSpanOverOneDay() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> statsService.onlineTrend(
                        1L, "2026-09-17 10:00:00", "2026-09-18 10:00:01"));
        assertEquals("查询跨度不能超过 1 天", ex.getMessage());
    }

    @Test
    void onlineTrend_allowsExactlyOneDay() {
        when(vhallClient.postForData(eq(StatsService.PATH_ONLINE_TREND), anyMap(), eq(Map.class)))
                .thenReturn(Map.of("list", List.of()));

        OnlineTrendResponse response = statsService.onlineTrend(
                1L, "2026-09-17 10:00:00", "2026-09-18 10:00:00");
        assertEquals(0, response.getPoints().size());
    }

    @Test
    void onlineTrend_requiresTimes() {
        assertThrows(IllegalArgumentException.class,
                () -> statsService.onlineTrend(1L, " ", "2026-09-17 11:00:00"));
    }

    @Test
    void onlineTrend_rejectsBadFormat() {
        assertThrows(IllegalArgumentException.class,
                () -> statsService.onlineTrend(1L, "2026-09-17 10:00", "2026-09-17 11:00:00"));
    }
}
