package com.zhibo.admin.course;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.zhibo.activity.ActivityOrderCourseEntity;
import com.zhibo.activity.ActivityOrderCourseRepository;
import com.zhibo.activity.ActivityOrderEntity;
import com.zhibo.activity.ActivityOrderRepository;
import com.zhibo.admin.course.dto.OutsideOrderReceiveItem;
import com.zhibo.viewer.ViewerEntity;
import com.zhibo.viewer.ViewerRepository;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class OutsideOrderServiceTest {

    @Mock
    private ActivityOrderRepository orderRepository;
    @Mock
    private ActivityOrderCourseRepository orderCourseRepository;
    @Mock
    private ViewerRepository viewerRepository;

    @InjectMocks
    private OutsideOrderService service;

    private final AtomicLong ids = new AtomicLong(100);

    @BeforeEach
    void stubSave() {
        lenient().when(orderRepository.saveAndFlush(any())).thenAnswer(invocation -> {
            ActivityOrderEntity entity = invocation.getArgument(0);
            if (entity.getId() == null) {
                entity.setId(ids.incrementAndGet());
            }
            return entity;
        });
        lenient().when(orderRepository.findByOrderNo(any())).thenReturn(Optional.empty());
        lenient().when(viewerRepository.findByUsername(any())).thenReturn(Optional.empty());
    }

    @Test
    void receive_nullOrEmpty_rejected() {
        IllegalArgumentException empty = assertThrows(
                IllegalArgumentException.class, () -> service.receive(List.of()));
        assertEquals("请求体必须是订单数组", empty.getMessage());
        IllegalArgumentException missing = assertThrows(
                IllegalArgumentException.class, () -> service.receive(null));
        assertEquals("请求体必须是订单数组", missing.getMessage());
        verify(orderRepository, never()).saveAndFlush(any());
    }

    @Test
    void receive_blankOrderId_rejected() {
        OutsideOrderReceiveItem item = validItem();
        item.setOrderId("  ");
        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class, () -> service.receive(List.of(item)));
        assertEquals("订单ID不能为空", ex.getMessage());
        verify(orderRepository, never()).saveAndFlush(any());
    }

    @Test
    void receive_blankUsername_rejected() {
        OutsideOrderReceiveItem item = validItem();
        item.setUsername(null);
        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class, () -> service.receive(List.of(item)));
        assertEquals("下单人用户名不能为空", ex.getMessage());
    }

    @Test
    void receive_unknownStatus_rejected() {
        OutsideOrderReceiveItem item = validItem();
        item.setOrderStatus(999);
        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class, () -> service.receive(List.of(item)));
        assertEquals("订单状态无效", ex.getMessage());
        verify(orderRepository, never()).saveAndFlush(any());
    }

    @Test
    void receive_badOrderTime_rejected() {
        OutsideOrderReceiveItem item = validItem();
        item.setOrderTime("2026/11/05 10:20:30");
        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class, () -> service.receive(List.of(item)));
        assertEquals("下单时间格式错误", ex.getMessage());
    }

    @Test
    void receive_liveRoomIdTooLong_rejected() {
        OutsideOrderReceiveItem item = validItem();
        item.setLiveRoomId("a".repeat(33));
        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class, () -> service.receive(List.of(item)));
        assertEquals("直播间ID不能超过32个字符", ex.getMessage());
    }

    @Test
    void receive_courseIdTooLong_rejected() {
        OutsideOrderReceiveItem item = validItem();
        item.setCourseIds(List.of("KC0001", "x".repeat(33)));
        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class, () -> service.receive(List.of(item)));
        assertEquals("课程ID不能超过32个字符", ex.getMessage());
    }

    @Test
    void receive_secondItemInvalid_rollsBackBeforeWrite() {
        OutsideOrderReceiveItem first = validItem();
        OutsideOrderReceiveItem second = validItem();
        second.setOrderId("ORD-2");
        second.setUsername("  ");
        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class, () -> service.receive(List.of(first, second)));
        assertEquals("下单人用户名不能为空", ex.getMessage());
        verify(orderRepository, never()).saveAndFlush(any());
    }

    @Test
    void mapStatus_storesOmsCodesAsIs() {
        assertEquals(1010, OutsideOrderService.mapStatus(1010, null));
        assertEquals(1030, OutsideOrderService.mapStatus(1030, null));
        assertEquals(1050, OutsideOrderService.mapStatus(1050, null));
        assertEquals(1070, OutsideOrderService.mapStatus(1070, null));
        assertEquals(1071, OutsideOrderService.mapStatus(1071, null));
        assertEquals(1080, OutsideOrderService.mapStatus(1080, null));
        assertEquals(1081, OutsideOrderService.mapStatus(1081, null));
        assertEquals(1030, OutsideOrderService.mapStatus(null, LocalDateTime.of(2026, 11, 5, 10, 21, 5)));
        assertEquals(1010, OutsideOrderService.mapStatus(null, null));
    }

    @Test
    void receive_insertsOrderAndCourseLinks() {
        OutsideOrderReceiveItem item = validItem();
        item.setCourseIds(List.of("KC0001", " KC0002 ", "KC0001", "", "  "));

        service.receive(List.of(item));

        ArgumentCaptor<ActivityOrderEntity> saved = ArgumentCaptor.forClass(ActivityOrderEntity.class);
        verify(orderRepository).saveAndFlush(saved.capture());
        ActivityOrderEntity entity = saved.getValue();
        assertEquals("20261105123000001", entity.getOrderNo());
        assertEquals("o3iNI9E93U6OVcjd", entity.getUsername());
        assertNull(entity.getUserNickname());
        assertEquals(24L, entity.getProductId());
        assertEquals("课程-商品", entity.getProductName());
        assertEquals("课程", entity.getProductType());
        assertEquals(new BigDecimal("998.00"), entity.getPayAmount());
        assertEquals("a1b2c3d4e5f60718293a4b5c6d7e8f90", entity.getActivityId());
        assertEquals("10086", entity.getSalesmanId());
        assertEquals(1, entity.getChannel());
        assertEquals(1030, entity.getOrderStatus());
        assertEquals(LocalDateTime.of(2026, 11, 5, 10, 20, 30), entity.getOrderTime());
        assertEquals(LocalDateTime.of(2026, 11, 5, 10, 21, 5), entity.getPayTime());

        verify(orderCourseRepository).deleteByOrderId(entity.getId());
        ArgumentCaptor<ActivityOrderCourseEntity> courses =
                ArgumentCaptor.forClass(ActivityOrderCourseEntity.class);
        verify(orderCourseRepository, times(2)).save(courses.capture());
        List<String> courseIds = courses.getAllValues().stream()
                .map(ActivityOrderCourseEntity::getCourseId)
                .toList();
        assertEquals(List.of("KC0001", "KC0002"), courseIds);
        assertEquals(entity.getId(), courses.getAllValues().get(0).getOrderId());
    }

    @Test
    void receive_activityMissing_stillStoresLiveRoomId() {
        OutsideOrderReceiveItem item = validItem();
        item.setLiveRoomId("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa");
        service.receive(List.of(item));
        ArgumentCaptor<ActivityOrderEntity> saved = ArgumentCaptor.forClass(ActivityOrderEntity.class);
        verify(orderRepository).saveAndFlush(saved.capture());
        assertEquals("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa", saved.getValue().getActivityId());
    }

    @Test
    void receive_idempotentUpdate_replacesCoursesAndStatus() {
        ActivityOrderEntity existing = new ActivityOrderEntity();
        existing.setId(7L);
        existing.setOrderNo("20261105123000001");
        existing.setUsername("old-user");
        existing.setOrderStatus(1010);
        when(orderRepository.findByOrderNo("20261105123000001")).thenReturn(Optional.of(existing));

        OutsideOrderReceiveItem item = validItem();
        item.setOrderStatus(1081);
        item.setCourseIds(List.of("KC0009"));
        item.setUsername("o3iNI9E93U6OVcjd");
        item.setTradeAmount(new BigDecimal("10.50"));

        service.receive(List.of(item));

        ArgumentCaptor<ActivityOrderEntity> saved = ArgumentCaptor.forClass(ActivityOrderEntity.class);
        verify(orderRepository).saveAndFlush(saved.capture());
        assertEquals(7L, saved.getValue().getId());
        assertEquals(1081, saved.getValue().getOrderStatus());
        assertEquals(new BigDecimal("10.50"), saved.getValue().getPayAmount());
        assertEquals("o3iNI9E93U6OVcjd", saved.getValue().getUsername());
        verify(orderCourseRepository).deleteByOrderId(7L);
        ArgumentCaptor<ActivityOrderCourseEntity> courses =
                ArgumentCaptor.forClass(ActivityOrderCourseEntity.class);
        verify(orderCourseRepository).save(courses.capture());
        assertEquals("KC0009", courses.getValue().getCourseId());
        assertEquals(7L, courses.getValue().getOrderId());
    }

    @Test
    void receive_snapshotsNicknameFromViewer_doesNotInventFromUsername() {
        ViewerEntity viewer = new ViewerEntity();
        viewer.setUsername("o3iNI9E93U6OVcjd");
        viewer.setNickname(" 小明 ");
        when(viewerRepository.findByUsername("o3iNI9E93U6OVcjd")).thenReturn(Optional.of(viewer));

        service.receive(List.of(validItem()));

        ArgumentCaptor<ActivityOrderEntity> saved = ArgumentCaptor.forClass(ActivityOrderEntity.class);
        verify(orderRepository).saveAndFlush(saved.capture());
        assertEquals("小明", saved.getValue().getUserNickname());
    }

    @Test
    void receive_viewerWithoutNickname_leavesNicknameNull() {
        ViewerEntity viewer = new ViewerEntity();
        viewer.setUsername("o3iNI9E93U6OVcjd");
        viewer.setNickname("  ");
        when(viewerRepository.findByUsername("o3iNI9E93U6OVcjd")).thenReturn(Optional.of(viewer));

        service.receive(List.of(validItem()));

        ArgumentCaptor<ActivityOrderEntity> saved = ArgumentCaptor.forClass(ActivityOrderEntity.class);
        verify(orderRepository).saveAndFlush(saved.capture());
        assertNull(saved.getValue().getUserNickname());
    }

    @Test
    void receive_omittedStatus_usesPayTimeDefault() {
        OutsideOrderReceiveItem unpaid = validItem();
        unpaid.setOrderId("UNPAID-1");
        unpaid.setOrderStatus(null);
        unpaid.setPayTime(null);
        service.receive(List.of(unpaid));
        ArgumentCaptor<ActivityOrderEntity> saved = ArgumentCaptor.forClass(ActivityOrderEntity.class);
        verify(orderRepository).saveAndFlush(saved.capture());
        assertEquals(1010, saved.getValue().getOrderStatus());
        assertNull(saved.getValue().getPayTime());
    }

    private static OutsideOrderReceiveItem validItem() {
        OutsideOrderReceiveItem item = new OutsideOrderReceiveItem();
        item.setOrderId("20261105123000001");
        item.setProductId(24L);
        item.setProductName("课程-商品");
        item.setProductType("课程");
        item.setTradeAmount(new BigDecimal("998.00"));
        item.setUsername("o3iNI9E93U6OVcjd");
        item.setLiveRoomId("a1b2c3d4e5f60718293a4b5c6d7e8f90");
        item.setCourseIds(new ArrayList<>(List.of("KC0001", "KC0002")));
        item.setOrderTime("2026-11-05 10:20:30");
        item.setPayTime("2026-11-05 10:21:05");
        item.setSalesmanId("10086");
        item.setChannel(1);
        item.setOrderStatus(1030);
        return item;
    }
}
