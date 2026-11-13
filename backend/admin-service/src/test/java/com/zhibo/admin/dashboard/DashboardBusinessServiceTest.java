package com.zhibo.admin.dashboard;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.zhibo.activity.ActivityOrderRepository;
import com.zhibo.admin.dashboard.dto.BusinessMetricsResponse;
import com.zhibo.viewer.ViewerRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DashboardBusinessServiceTest {

    private static final ZoneId SHANGHAI = ZoneId.of("Asia/Shanghai");

    /** 2026-11-12 00:30 Asia/Shanghai == 2026-11-11T16:30:00Z，用于验证「今日」按上海自然日。 */
    private static final Instant FIXED_INSTANT = Instant.parse("2026-11-11T16:30:00Z");

    private static final LocalDateTime TODAY_START = LocalDateTime.of(2026, 11, 12, 0, 0, 0);
    private static final LocalDateTime TODAY_END = LocalDateTime.of(2026, 11, 13, 0, 0, 0);

    @Mock
    private ViewerRepository viewerRepository;
    @Mock
    private ActivityOrderRepository activityOrderRepository;

    private DashboardBusinessService service;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(FIXED_INSTANT, ZoneId.of("UTC"));
        service = new DashboardBusinessService(viewerRepository, activityOrderRepository, clock);
    }

    @Test
    void today_window_is_shanghai_calendar_day() {
        stubEmpty();

        BusinessMetricsResponse resp = service.metrics();

        verify(viewerRepository)
                .countByLastVisitTimeGreaterThanEqualAndLastVisitTimeLessThan(
                        TODAY_START, TODAY_END);
        verify(viewerRepository)
                .countByCreatedTimeGreaterThanEqualAndCreatedTimeLessThan(
                        TODAY_START, TODAY_END);
        verify(activityOrderRepository)
                .sumPayAmountByStatusesAndPayTimeBetween(
                        DashboardBusinessService.PAID_STATUSES, TODAY_START, TODAY_END);
        verify(activityOrderRepository)
                .countByStatusesAndPayTimeBetween(
                        DashboardBusinessService.PAID_STATUSES, TODAY_START, TODAY_END);
        assertEquals("2026-11-12 00:30:00", resp.getStatsTime());
    }

    @Test
    void paid_statuses_exclude_pending() {
        stubEmpty();

        service.metrics();

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Integer>> statuses = ArgumentCaptor.forClass(List.class);
        verify(activityOrderRepository)
                .sumPayAmountByStatuses(statuses.capture());
        assertEquals(List.of(2, 3, 4, 5), statuses.getValue());
        assertFalse(statuses.getValue().contains(1));

        verify(activityOrderRepository)
                .sumPayAmountByStatusesAndPayTimeBetween(
                        eq(List.of(2, 3, 4, 5)), eq(TODAY_START), eq(TODAY_END));
        verify(activityOrderRepository)
                .countByStatusesAndPayTimeBetween(
                        eq(List.of(2, 3, 4, 5)), eq(TODAY_START), eq(TODAY_END));
        verify(activityOrderRepository)
                .countDistinctUsernameByStatuses(eq(List.of(2, 3, 4, 5)));
    }

    @Test
    void empty_tables_return_zeros() {
        when(viewerRepository.countByLastVisitTimeGreaterThanEqualAndLastVisitTimeLessThan(
                        TODAY_START, TODAY_END))
                .thenReturn(0L);
        when(viewerRepository.countByCreatedTimeGreaterThanEqualAndCreatedTimeLessThan(
                        TODAY_START, TODAY_END))
                .thenReturn(0L);
        when(viewerRepository.count()).thenReturn(0L);
        when(activityOrderRepository.sumPayAmountByStatusesAndPayTimeBetween(
                        DashboardBusinessService.PAID_STATUSES, TODAY_START, TODAY_END))
                .thenReturn(null);
        when(activityOrderRepository.countByStatusesAndPayTimeBetween(
                        DashboardBusinessService.PAID_STATUSES, TODAY_START, TODAY_END))
                .thenReturn(0L);
        when(activityOrderRepository.countDistinctUsernameByStatuses(
                        DashboardBusinessService.PAID_STATUSES))
                .thenReturn(0L);
        when(activityOrderRepository.sumPayAmountByStatuses(DashboardBusinessService.PAID_STATUSES))
                .thenReturn(null);

        BusinessMetricsResponse resp = service.metrics();

        assertEquals(0L, resp.getTodayVisitorCount());
        assertEquals(0L, resp.getTodayNewUserCount());
        assertEquals("0.00", resp.getTodayPayAmount());
        assertEquals(0L, resp.getTodayOrderCount());
        assertEquals(0L, resp.getTotalPayUserCount());
        assertEquals(0L, resp.getTotalUserCount());
        assertEquals("0.00", resp.getTotalPayAmount());
    }

    @Test
    void today_and_total_metrics_map_and_format_amounts() {
        when(viewerRepository.countByLastVisitTimeGreaterThanEqualAndLastVisitTimeLessThan(
                        TODAY_START, TODAY_END))
                .thenReturn(2870L);
        when(viewerRepository.countByCreatedTimeGreaterThanEqualAndCreatedTimeLessThan(
                        TODAY_START, TODAY_END))
                .thenReturn(90L);
        when(viewerRepository.count()).thenReturn(218870L);
        when(activityOrderRepository.sumPayAmountByStatusesAndPayTimeBetween(
                        DashboardBusinessService.PAID_STATUSES, TODAY_START, TODAY_END))
                .thenReturn(new BigDecimal("26800"));
        when(activityOrderRepository.countByStatusesAndPayTimeBetween(
                        DashboardBusinessService.PAID_STATUSES, TODAY_START, TODAY_END))
                .thenReturn(1L);
        when(activityOrderRepository.countDistinctUsernameByStatuses(
                        DashboardBusinessService.PAID_STATUSES))
                .thenReturn(15870L);
        when(activityOrderRepository.sumPayAmountByStatuses(DashboardBusinessService.PAID_STATUSES))
                .thenReturn(new BigDecimal("111126800"));

        BusinessMetricsResponse resp = service.metrics();

        assertEquals(2870L, resp.getTodayVisitorCount());
        assertEquals(90L, resp.getTodayNewUserCount());
        assertEquals("26800.00", resp.getTodayPayAmount());
        assertEquals(1L, resp.getTodayOrderCount());
        assertEquals(15870L, resp.getTotalPayUserCount());
        assertEquals(218870L, resp.getTotalUserCount());
        assertEquals("111126800.00", resp.getTotalPayAmount());
        assertEquals("2026-11-12 00:30:00", resp.getStatsTime());
    }

    @Test
    void total_pay_users_uses_distinct_username() {
        stubEmpty();
        when(activityOrderRepository.countDistinctUsernameByStatuses(
                        DashboardBusinessService.PAID_STATUSES))
                .thenReturn(1L);

        BusinessMetricsResponse resp = service.metrics();

        assertEquals(1L, resp.getTotalPayUserCount());
        verify(activityOrderRepository)
                .countDistinctUsernameByStatuses(DashboardBusinessService.PAID_STATUSES);
    }

    @Test
    void formatAmount_treats_null_as_zero_and_keeps_two_decimals() {
        assertEquals("0.00", DashboardBusinessService.formatAmount(null));
        assertEquals("0.00", DashboardBusinessService.formatAmount(BigDecimal.ZERO));
        assertEquals("0.10", DashboardBusinessService.formatAmount(new BigDecimal("0.1")));
        assertEquals("26800.00", DashboardBusinessService.formatAmount(new BigDecimal("26800")));
    }

    private void stubEmpty() {
        when(viewerRepository.countByLastVisitTimeGreaterThanEqualAndLastVisitTimeLessThan(
                        TODAY_START, TODAY_END))
                .thenReturn(0L);
        when(viewerRepository.countByCreatedTimeGreaterThanEqualAndCreatedTimeLessThan(
                        TODAY_START, TODAY_END))
                .thenReturn(0L);
        when(viewerRepository.count()).thenReturn(0L);
        when(activityOrderRepository.sumPayAmountByStatusesAndPayTimeBetween(
                        DashboardBusinessService.PAID_STATUSES, TODAY_START, TODAY_END))
                .thenReturn(BigDecimal.ZERO);
        when(activityOrderRepository.countByStatusesAndPayTimeBetween(
                        DashboardBusinessService.PAID_STATUSES, TODAY_START, TODAY_END))
                .thenReturn(0L);
        when(activityOrderRepository.countDistinctUsernameByStatuses(
                        DashboardBusinessService.PAID_STATUSES))
                .thenReturn(0L);
        when(activityOrderRepository.sumPayAmountByStatuses(DashboardBusinessService.PAID_STATUSES))
                .thenReturn(BigDecimal.ZERO);
    }
}
