package com.zhibo.admin.console;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.zhibo.activity.ActivityEntity;
import com.zhibo.activity.ActivityOrderRepository;
import com.zhibo.activity.ActivityProductEntity;
import com.zhibo.activity.ActivityProductId;
import com.zhibo.activity.ActivityProductRepository;
import com.zhibo.activity.ActivityRepository;
import com.zhibo.admin.activity.ActivityService;
import com.zhibo.admin.console.dto.ConsoleCartEnabledRequest;
import com.zhibo.admin.console.dto.ConsoleProductVisibleRequest;
import com.zhibo.admin.dashboard.DashboardBusinessService;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ConsoleProductServiceTest {

    private static final String ID = "a".repeat(32);

    @Mock
    private ActivityService activityService;
    @Mock
    private ActivityRepository activityRepository;
    @Mock
    private ActivityProductRepository productRepository;
    @Mock
    private ActivityOrderRepository orderRepository;

    @InjectMocks
    private ConsoleProductService service;

    private ActivityEntity activity;

    @BeforeEach
    void setUp() {
        activity = new ActivityEntity();
        activity.setId(ID);
        activity.setCartEnabled(true);
    }

    @Test
    void list_returnsBoundWithSoldAndCart() {
        when(activityService.requireActivity(ID)).thenReturn(activity);
        ActivityProductEntity p1 = product("20002", "课A", 0, true);
        ActivityProductEntity p2 = product("20003", "课B", 1, false);
        when(productRepository.findByActivityIds(List.of(ID))).thenReturn(List.of(p1, p2));
        List<Object[]> soldRows = new java.util.ArrayList<>();
        soldRows.add(new Object[] {20002L, 7L});
        when(orderRepository.countPaidOrdersByProduct(eq(ID), eq(DashboardBusinessService.PAID_STATUSES)))
                .thenReturn(soldRows);

        var res = service.list(ID);
        assertEquals(ID, res.getId());
        assertTrue(res.isCartEnabled());
        assertEquals(2, res.getList().size());
        assertEquals("20002", res.getList().get(0).getSpuId());
        assertEquals(7L, res.getList().get(0).getSoldCount());
        assertTrue(res.getList().get(0).isVisible());
        assertFalse(res.getList().get(1).isVisible());
        assertEquals(0L, res.getList().get(1).getSoldCount());
    }

    @Test
    void setVisible_updatesRow() {
        when(activityService.requireActivity(ID)).thenReturn(activity);
        ActivityProductEntity row = product("20002", "课A", 0, true);
        when(productRepository.findById(new ActivityProductId(ID, "20002"))).thenReturn(Optional.of(row));

        ConsoleProductVisibleRequest body = new ConsoleProductVisibleRequest();
        body.setSpuId("20002");
        body.setVisible(false);
        var res = service.setVisible(ID, body);
        assertFalse(res.isVisible());
        ArgumentCaptor<ActivityProductEntity> cap = ArgumentCaptor.forClass(ActivityProductEntity.class);
        verify(productRepository).save(cap.capture());
        assertFalse(Boolean.TRUE.equals(cap.getValue().getVisible()));
    }

    @Test
    void setVisible_unbound_throws() {
        when(activityService.requireActivity(ID)).thenReturn(activity);
        when(productRepository.findById(any())).thenReturn(Optional.empty());
        ConsoleProductVisibleRequest body = new ConsoleProductVisibleRequest();
        body.setSpuId("999");
        body.setVisible(true);
        assertThrows(IllegalArgumentException.class, () -> service.setVisible(ID, body));
    }

    @Test
    void setCartEnabled_persists() {
        when(activityService.requireActivity(ID)).thenReturn(activity);
        ConsoleCartEnabledRequest body = new ConsoleCartEnabledRequest();
        body.setEnabled(false);
        var res = service.setCartEnabled(ID, body);
        assertFalse(res.isCartEnabled());
        verify(activityRepository).save(activity);
        assertFalse(Boolean.TRUE.equals(activity.getCartEnabled()));
    }

    private static ActivityProductEntity product(String spuId, String name, int sort, boolean visible) {
        ActivityProductEntity row = new ActivityProductEntity();
        row.setId(new ActivityProductId(ID, spuId));
        row.setProductName(name);
        row.setSortOrder(sort);
        row.setVisible(visible);
        return row;
    }
}
