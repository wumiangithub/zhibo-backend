package com.zhibo.admin.console;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.zhibo.activity.ActivityEntity;
import com.zhibo.activity.ActivityOrderRepository;
import com.zhibo.activity.ActivityProductClickRepository;
import com.zhibo.admin.activity.ActivityService;
import com.zhibo.admin.console.dto.ConsoleTradeStatsResponse;
import com.zhibo.admin.dashboard.DashboardBusinessService;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ConsoleTradeStatsServiceTest {

    private static final String ID = "a1b2c3d4e5f67890abcdef1234567890";

    @Mock
    private ActivityService activityService;
    @Mock
    private ActivityOrderRepository orderRepository;
    @Mock
    private ActivityProductClickRepository clickRepository;

    private ConsoleTradeStatsService service;

    @BeforeEach
    void setUp() {
        service = new ConsoleTradeStatsService(activityService, orderRepository, clickRepository);
    }

    @Test
    void aggregatesKpisFromOrdersAndClicks() {
        ActivityEntity activity = new ActivityEntity();
        activity.setId(ID);
        when(activityService.requireActivity(ID)).thenReturn(activity);
        when(orderRepository.sumPayAmountByActivityAndStatuses(
                        eq(ID), eq(DashboardBusinessService.PAID_STATUSES)))
                .thenReturn(new BigDecimal("300.00"));
        when(orderRepository.countDistinctUsernameByActivityAndStatuses(
                        eq(ID), eq(DashboardBusinessService.PAID_STATUSES)))
                .thenReturn(3L);
        when(orderRepository.countByActivityAndStatuses(
                        eq(ID), eq(DashboardBusinessService.PAID_STATUSES)))
                .thenReturn(5L);
        when(clickRepository.countDistinctUsernameByActivityId(ID)).thenReturn(10L);
        when(orderRepository.countDistinctUsernameByActivityAndStatuses(
                        eq(ID), eq(com.zhibo.activity.ActivityOrderStatuses.UNPAID_STATUSES)))
                .thenReturn(2L);

        ConsoleTradeStatsResponse resp = service.stats(ID);

        assertEquals(ID, resp.getId());
        assertEquals(3L, resp.getDealUserCount());
        assertEquals(5L, resp.getDealProductCount());
        assertEquals(10L, resp.getProductClickUserCount());
        assertEquals("30.0", resp.getDealConversionRate());
        assertEquals("100.00", resp.getAvgOrderAmount());
        assertEquals(2L, resp.getUnpaidUserCount());
    }

    @Test
    void zerosWhenNoOrdersOrClicks() {
        ActivityEntity activity = new ActivityEntity();
        activity.setId(ID);
        when(activityService.requireActivity(ID)).thenReturn(activity);
        when(orderRepository.sumPayAmountByActivityAndStatuses(
                        eq(ID), eq(DashboardBusinessService.PAID_STATUSES)))
                .thenReturn(BigDecimal.ZERO);
        when(orderRepository.countDistinctUsernameByActivityAndStatuses(
                        eq(ID), eq(DashboardBusinessService.PAID_STATUSES)))
                .thenReturn(0L);
        when(orderRepository.countByActivityAndStatuses(
                        eq(ID), eq(DashboardBusinessService.PAID_STATUSES)))
                .thenReturn(0L);
        when(clickRepository.countDistinctUsernameByActivityId(ID)).thenReturn(0L);
        when(orderRepository.countDistinctUsernameByActivityAndStatuses(
                        eq(ID), eq(com.zhibo.activity.ActivityOrderStatuses.UNPAID_STATUSES)))
                .thenReturn(0L);

        ConsoleTradeStatsResponse resp = service.stats(ID);

        assertEquals("0.0", resp.getDealConversionRate());
        assertEquals("0.00", resp.getAvgOrderAmount());
        assertEquals(0L, resp.getUnpaidUserCount());
    }

    @Test
    void formatHelpers() {
        assertEquals("0.0", ConsoleTradeStatsService.formatConversionRate(5, 0));
        assertEquals("50.0", ConsoleTradeStatsService.formatConversionRate(1, 2));
        assertEquals("0.00", ConsoleTradeStatsService.formatAvgOrderAmount(new BigDecimal("99"), 0));
        assertEquals("33.33", ConsoleTradeStatsService.formatAvgOrderAmount(new BigDecimal("100"), 3));
    }
}
