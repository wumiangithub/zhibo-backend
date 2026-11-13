package com.zhibo.admin.commerce;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.eq;
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
import java.math.BigDecimal;
import java.util.List;
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

    private CommerceDataService service;

    @BeforeEach
    void setUp() {
        service = new CommerceDataService(
                activityService, productRepository, clickRepository, orderRepository);
    }

    @Test
    void listsBoundProducts_withClickAndDealAggregates_refundAlwaysZero() {
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
    }

    private static ActivityProductEntity product(String spuId, String name, int sort) {
        ActivityProductEntity row = new ActivityProductEntity();
        row.setId(new ActivityProductId(ID, spuId));
        row.setProductName(name);
        row.setSortOrder(sort);
        return row;
    }
}
