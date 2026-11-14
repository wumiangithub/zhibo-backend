package com.zhibo.admin.commerce;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.zhibo.activity.ActivityEntity;
import com.zhibo.activity.ActivityOrderRepository;
import com.zhibo.activity.ActivityProductClickRepository;
import com.zhibo.activity.ActivityProductEntity;
import com.zhibo.activity.ActivityProductId;
import com.zhibo.activity.ActivityProductRepository;
import com.zhibo.admin.activity.ActivityService;
import com.zhibo.admin.commerce.dto.ActivityCommerceDataResponse;
import com.zhibo.admin.dashboard.DashboardBusinessService;
import com.zhibo.admin.oms.OmsRefundQuery;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CommerceDataServiceTest {

    private static final String ID = "a1b2c3d4e5f67890abcdef1234567890";

    @Mock
    private ActivityService activityService;
    @Mock
    private ActivityProductRepository productRepository;
    @Mock
    private ActivityProductClickRepository clickRepository;
    @Mock
    private ActivityOrderRepository orderRepository;
    @Mock
    private OmsRefundQuery omsRefundQuery;

    private CommerceDataService service;

    @BeforeEach
    void setUp() {
        service = new CommerceDataService(
                activityService, productRepository, clickRepository, orderRepository, omsRefundQuery);
    }

    @Test
    void listsBoundProducts_withClickAndDealAggregates_refundZeroWhenOmsUnavailable() {
        ActivityEntity activity = new ActivityEntity();
        activity.setId(ID);
        when(activityService.requireActivity(ID)).thenReturn(activity);

        ActivityProductEntity p1 = product("20002", "课程A", 0);
        ActivityProductEntity p2 = product("20003", "课程B", 1);
        when(productRepository.findByActivityIds(List.of(ID))).thenReturn(List.of(p1, p2));
        List<Object[]> clickAgg = List.<Object[]>of(new Object[] {"20002", 5L, 3L});
        when(clickRepository.aggregateBySpu(ID)).thenReturn(clickAgg);
        List<Object[]> dealAgg = List.<Object[]>of(
                new Object[] {20002L, 2L, new BigDecimal("99.50")});
        when(orderRepository.aggregatePaidByProduct(eq(ID), eq(DashboardBusinessService.PAID_STATUSES)))
                .thenReturn(dealAgg);

        ActivityCommerceDataResponse resp = service.commerceData(ID);

        assertEquals(ID, resp.getId());
        assertEquals(2, resp.getList().size());

        ActivityCommerceDataResponse.Row a = resp.getList().get(0);
        assertEquals("20002", a.getSpuId());
        assertEquals("课程A", a.getProductName());
        assertEquals(5L, a.getClickCount());
        assertEquals(3L, a.getClickUserCount());
        assertEquals(2L, a.getDealUserCount());
        assertEquals("99.50", a.getDealAmount());
        assertEquals("0.00", a.getRefundAmount());

        ActivityCommerceDataResponse.Row b = resp.getList().get(1);
        assertEquals("20003", b.getSpuId());
        assertEquals(0L, b.getClickCount());
        assertEquals(0L, b.getClickUserCount());
        assertEquals(0L, b.getDealUserCount());
        assertEquals("0.00", b.getDealAmount());
        assertEquals("0.00", b.getRefundAmount());

        verify(omsRefundQuery, never()).sumCompletedRefundMoney(any());
        verify(orderRepository, never()).findOrderNoAndProductIdByActivityId(any());
    }

    @Test
    void emptyWhenNoBoundProducts() {
        ActivityEntity activity = new ActivityEntity();
        activity.setId(ID);
        when(activityService.requireActivity(ID)).thenReturn(activity);
        when(productRepository.findByActivityIds(List.of(ID))).thenReturn(List.of());
        when(clickRepository.aggregateBySpu(ID)).thenReturn(List.of());
        when(orderRepository.aggregatePaidByProduct(eq(ID), eq(DashboardBusinessService.PAID_STATUSES)))
                .thenReturn(List.of());

        ActivityCommerceDataResponse resp = service.commerceData(ID);
        assertEquals(0, resp.getList().size());
        verify(omsRefundQuery, never()).sumCompletedRefundMoney(any());
        verify(orderRepository, never()).findOrderNoAndProductIdByActivityId(any());
    }

    @Test
    void sumsCompletedRefundsPerProduct() {
        stubActivityAndProducts();
        when(omsRefundQuery.isAvailable()).thenReturn(true);
        when(orderRepository.findOrderNoAndProductIdByActivityId(ID))
                .thenReturn(List.<Object[]>of(
                        new Object[] {"ORD-1", 20002L},
                        new Object[] {"ORD-2", 20003L},
                        new Object[] {"ORD-3", 20002L}));
        when(omsRefundQuery.sumCompletedRefundMoney(List.of("ORD-1", "ORD-2", "ORD-3")))
                .thenReturn(Map.of(
                        "ORD-1", new BigDecimal("10.00"),
                        "ORD-2", new BigDecimal("3.50"),
                        "ORD-3", new BigDecimal("0.25")));

        ActivityCommerceDataResponse resp = service.commerceData(ID);

        assertEquals("10.25", resp.getList().get(0).getRefundAmount());
        assertEquals("3.50", resp.getList().get(1).getRefundAmount());
        assertEquals("99.50", resp.getList().get(0).getDealAmount());
    }

    @Test
    void oneOrderWithMultipleRefundListRows_usesRepositorySum() {
        stubActivityAndProducts();
        when(omsRefundQuery.isAvailable()).thenReturn(true);
        when(orderRepository.findOrderNoAndProductIdByActivityId(ID))
                .thenReturn(List.<Object[]>of(new Object[] {"ORD-1", 20002L}));
        // 仓库已对同一订单多条流水 SUM(refund_order_money)
        when(omsRefundQuery.sumCompletedRefundMoney(List.of("ORD-1")))
                .thenReturn(Map.of("ORD-1", new BigDecimal("1.50")));

        ActivityCommerceDataResponse resp = service.commerceData(ID);

        assertEquals("1.50", resp.getList().get(0).getRefundAmount());
        assertEquals("0.00", resp.getList().get(1).getRefundAmount());
    }

    @Test
    void omsFailureLeavesRefundZero_andKeepsDealData() {
        stubActivityAndProducts();
        when(omsRefundQuery.isAvailable()).thenReturn(true);
        when(orderRepository.findOrderNoAndProductIdByActivityId(ID))
                .thenReturn(List.<Object[]>of(new Object[] {"ORD-1", 20002L}));
        when(omsRefundQuery.sumCompletedRefundMoney(List.of("ORD-1")))
                .thenThrow(new RuntimeException("OMS 连接超时"));

        ActivityCommerceDataResponse resp = service.commerceData(ID);

        assertEquals("0.00", resp.getList().get(0).getRefundAmount());
        assertEquals("0.00", resp.getList().get(1).getRefundAmount());
        assertEquals("99.50", resp.getList().get(0).getDealAmount());
        assertEquals(2L, resp.getList().get(0).getDealUserCount());
        assertEquals(5L, resp.getList().get(0).getClickCount());
    }

    @Test
    void noOrdersSkipsOmsQuery() {
        stubActivityAndProducts();
        when(omsRefundQuery.isAvailable()).thenReturn(true);
        when(orderRepository.findOrderNoAndProductIdByActivityId(ID)).thenReturn(List.of());

        ActivityCommerceDataResponse resp = service.commerceData(ID);

        assertEquals("0.00", resp.getList().get(0).getRefundAmount());
        assertEquals("0.00", resp.getList().get(1).getRefundAmount());
        verify(omsRefundQuery, never()).sumCompletedRefundMoney(any());
    }

    @Test
    void fullyRefundedOrderStillContributes_evenIfNotInDealAggregate() {
        stubActivityAndProducts();
        when(omsRefundQuery.isAvailable()).thenReturn(true);
        // ORD-FULL 已全部退款，不出现在成交聚合里，但仍计入成功退款
        when(orderRepository.findOrderNoAndProductIdByActivityId(ID))
                .thenReturn(List.<Object[]>of(
                        new Object[] {"ORD-1", 20002L},
                        new Object[] {"ORD-FULL", 20003L}));
        when(omsRefundQuery.sumCompletedRefundMoney(List.of("ORD-1", "ORD-FULL")))
                .thenReturn(Map.of(
                        "ORD-1", new BigDecimal("1.00"),
                        "ORD-FULL", new BigDecimal("88.00")));

        ActivityCommerceDataResponse resp = service.commerceData(ID);

        assertEquals("1.00", resp.getList().get(0).getRefundAmount());
        assertEquals("88.00", resp.getList().get(1).getRefundAmount());
        assertEquals("0.00", resp.getList().get(1).getDealAmount());
    }

    private void stubActivityAndProducts() {
        ActivityEntity activity = new ActivityEntity();
        activity.setId(ID);
        when(activityService.requireActivity(ID)).thenReturn(activity);
        ActivityProductEntity p1 = product("20002", "课程A", 0);
        ActivityProductEntity p2 = product("20003", "课程B", 1);
        when(productRepository.findByActivityIds(List.of(ID))).thenReturn(List.of(p1, p2));
        when(clickRepository.aggregateBySpu(ID))
                .thenReturn(List.<Object[]>of(new Object[] {"20002", 5L, 3L}));
        when(orderRepository.aggregatePaidByProduct(eq(ID), eq(DashboardBusinessService.PAID_STATUSES)))
                .thenReturn(List.<Object[]>of(new Object[] {20002L, 2L, new BigDecimal("99.50")}));
    }

    private static ActivityProductEntity product(String spuId, String name, int sort) {
        ActivityProductEntity row = new ActivityProductEntity();
        row.setId(new ActivityProductId(ID, spuId));
        row.setProductName(name);
        row.setSortOrder(sort);
        return row;
    }
}
