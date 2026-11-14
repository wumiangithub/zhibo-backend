package com.zhibo.admin.console;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.zhibo.activity.ActivityEntity;
import com.zhibo.activity.ActivityOrderRepository;
import com.zhibo.admin.activity.ActivityService;
import com.zhibo.admin.console.dto.ConsoleWatchTradeResponse;
import com.zhibo.admin.dashboard.DashboardBusinessService;
import java.math.BigDecimal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ConsoleWatchTradeServiceTest {

    private static final String ID = "a1b2c3d4e5f67890abcdef1234567890";

    @Mock
    private ActivityService activityService;
    @Mock
    private ActivityOrderRepository orderRepository;

    private ConsoleWatchTradeService service;

    @BeforeEach
    void setUp() {
        service = new ConsoleWatchTradeService(activityService, orderRepository);
    }

    @Test
    void aggregatesPaidOrdersForActivity() {
        ActivityEntity activity = new ActivityEntity();
        activity.setId(ID);
        when(activityService.requireActivity(ID)).thenReturn(activity);
        when(orderRepository.sumPayAmountByActivityAndStatuses(
                        eq(ID), eq(DashboardBusinessService.PAID_STATUSES)))
                .thenReturn(new BigDecimal("26800.5"));
        when(orderRepository.countDistinctUsernameByActivityAndStatuses(
                        eq(ID), eq(DashboardBusinessService.PAID_STATUSES)))
                .thenReturn(3L);
        when(orderRepository.countByActivityAndStatuses(
                        eq(ID), eq(DashboardBusinessService.PAID_STATUSES)))
                .thenReturn(5L);

        ConsoleWatchTradeResponse resp = service.watchTrade(ID);

        assertEquals(ID, resp.getId());
        assertEquals("26800.50", resp.getDealAmount());
        assertEquals(3L, resp.getDealUserCount());
        assertEquals(5L, resp.getDealProductCount());
    }

    @Test
    void zerosWhenNoOrders() {
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

        ConsoleWatchTradeResponse resp = service.watchTrade(ID);

        assertEquals("0.00", resp.getDealAmount());
        assertEquals(0L, resp.getDealUserCount());
        assertEquals(0L, resp.getDealProductCount());
    }
}
