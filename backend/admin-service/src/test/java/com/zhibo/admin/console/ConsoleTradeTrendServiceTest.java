package com.zhibo.admin.console;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.zhibo.activity.ActivityEntity;
import com.zhibo.activity.ActivityOrderRepository;
import com.zhibo.admin.activity.ActivityService;
import com.zhibo.admin.console.dto.ConsoleTradeTrendResponse;
import com.zhibo.admin.dashboard.DashboardBusinessService;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ConsoleTradeTrendServiceTest {

    private static final String ID = "a1b2c3d4e5f67890abcdef1234567890";

    @Mock
    private ActivityService activityService;
    @Mock
    private ActivityOrderRepository orderRepository;

    private ConsoleTradeTrendService service;

    @BeforeEach
    void setUp() {
        service = new ConsoleTradeTrendService(activityService, orderRepository);
    }

    @Test
    void buildsPointsForRange() {
        ActivityEntity activity = new ActivityEntity();
        activity.setId(ID);
        when(activityService.requireActivity(ID)).thenReturn(activity);
        LocalDateTime start = LocalDateTime.of(2026, 10, 22, 11, 0, 0);
        LocalDateTime end = LocalDateTime.of(2026, 10, 22, 11, 2, 0);
        List<Object[]> rows = List.<Object[]>of(
                new Object[] {
                    LocalDateTime.of(2026, 10, 22, 11, 0, 15), new BigDecimal("99.00")
                });
        when(orderRepository.findPaidPayTimeAndAmount(
                        eq(ID),
                        eq(DashboardBusinessService.PAID_STATUSES),
                        eq(start),
                        eq(end)))
                .thenReturn(rows);

        ConsoleTradeTrendResponse resp =
                service.trend(ID, "2026-10-22 11:00:00", "2026-10-22 11:02:00", 60);

        assertEquals(ID, resp.getId());
        assertEquals(60, resp.getBucketSeconds());
        assertEquals(2, resp.getPoints().size());
        assertEquals("99.00", resp.getPoints().get(0).getDealAmount());
        assertEquals(1, resp.getPoints().get(0).getSoldCount());
        assertEquals("0.00", resp.getPoints().get(1).getDealAmount());
        assertEquals(0, resp.getPoints().get(1).getSoldCount());
    }

    @Test
    void rejectsSpanOverOneDay() {
        ActivityEntity activity = new ActivityEntity();
        activity.setId(ID);
        when(activityService.requireActivity(ID)).thenReturn(activity);

        assertThrows(
                IllegalArgumentException.class,
                () -> service.trend(ID, "2026-10-22 11:00:00", "2026-10-23 11:00:01", 60));
    }
}
