package com.zhibo.admin.stats;

import com.zhibo.admin.stats.dto.ActivityStatsResponse;
import com.zhibo.admin.stats.dto.OnlineTrendResponse;
import com.zhibo.vhall.client.VhallClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StatsServiceTest {

    @Mock
    private VhallClient vhallClient;

    @InjectMocks
    private StatsService statsService;

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
    void onlineTrend_mapsPoints() {
        when(vhallClient.postForData(eq(StatsService.PATH_ONLINE_TREND), anyMap(), eq(Map.class)))
                .thenReturn(Map.of("list", List.of(
                        Map.of("time", "2026-09-17 10:00:00", "total", 3),
                        Map.of("time", "2026-09-17 10:01:00", "total", 5)
                )));

        OnlineTrendResponse response = statsService.onlineTrend(
                9L, "2026-09-17 10:00:00", "2026-09-17 11:00:00");
        assertEquals(9L, response.getId());
        assertEquals(2, response.getPoints().size());
        assertEquals("2026-09-17 10:01:00", response.getPoints().get(1).getTime());
        assertEquals(5, response.getPoints().get(1).getCount());
    }

    @Test
    void onlineTrend_requiresTimes() {
        assertThrows(IllegalArgumentException.class,
                () -> statsService.onlineTrend(1L, " ", "2026-09-17 11:00:00"));
    }
}
