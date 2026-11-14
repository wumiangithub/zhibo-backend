package com.zhibo.admin.console;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import com.zhibo.activity.ActivityAudienceEntity;
import com.zhibo.activity.ActivityAudienceRepository;
import com.zhibo.activity.ActivityEntity;
import com.zhibo.activity.ActivityOrderEntity;
import com.zhibo.activity.ActivityOrderRepository;
import com.zhibo.activity.ActivityProductClickRepository;
import com.zhibo.activity.ActivityRepository;
import com.zhibo.admin.auth.SysUserAccountEntity;
import com.zhibo.admin.auth.SysUserAccountRepository;
import com.zhibo.admin.console.dto.ConsoleCommerceDetailItem;
import com.zhibo.admin.console.dto.ConsoleCommerceDetailListResponse;
import com.zhibo.admin.console.dto.ConsoleSessionInviterListResponse;
import com.zhibo.viewer.ViewerEntity;
import com.zhibo.viewer.ViewerRepository;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ConsoleCommerceDetailServiceTest {

    private static final String ROOM_ID = "a1b2c3d4e5f67890abcdef1234567890";
    private static final String USER_A = "qk_u_aaaa1111";
    private static final String USER_B = "qk_u_bbbb2222";

    @Mock
    private ActivityRepository activityRepository;
    @Mock
    private ActivityOrderRepository orderRepository;
    @Mock
    private ActivityProductClickRepository productClickRepository;
    @Mock
    private ActivityAudienceRepository audienceRepository;
    @Mock
    private ViewerRepository viewerRepository;
    @Mock
    private SysUserAccountRepository userRepository;

    @InjectMocks
    private ConsoleCommerceDetailService service;

    @BeforeEach
    void stubBasics() {
        ActivityEntity activity = new ActivityEntity();
        activity.setId(ROOM_ID);
        activity.setState(1);
        lenient().when(activityRepository.findById(ROOM_ID)).thenReturn(Optional.of(activity));
        lenient().when(productClickRepository.aggregateByUsernameAndSpu(eq(ROOM_ID), anyCollection()))
                .thenReturn(List.<Object[]>of());
        lenient().when(orderRepository.aggregatePaidByUsernames(anyCollection(), any()))
                .thenReturn(List.<Object[]>of());
        lenient().when(audienceRepository.findByActivityIdAndUsernameIn(eq(ROOM_ID), anyCollection()))
                .thenReturn(List.of());
        lenient().when(viewerRepository.findByUsernameIn(anyCollection())).thenReturn(List.of());
        lenient().when(userRepository.findAllById(anyCollection())).thenReturn(List.of());
    }

    @Test
    void list_aggregatesByUsernameAndSpu_attachesClicks_andSitewidePaid() {
        when(orderRepository.findByActivityIdWithProduct(ROOM_ID)).thenReturn(List.of(
                order(USER_A, 20002L, "商品甲", 1010, null, "2026-09-30 10:00:00"),
                order(USER_A, 20002L, "商品甲", 1030, "99.00", "2026-09-30 10:05:00"),
                order(USER_A, 20002L, "商品甲新名", 1081, "50.00", "2026-09-30 10:10:00"),
                order(USER_B, 30001L, "商品乙", 1010, null, "2026-09-30 11:00:00"),
                order(USER_A, null, "无商品", 1030, "1.00", "2026-09-30 12:00:00")));

        when(productClickRepository.aggregateByUsernameAndSpu(eq(ROOM_ID), anyCollection()))
                .thenReturn(List.<Object[]>of(
                        new Object[] {USER_A, "20002", 3L, LocalDateTime.of(2026, 9, 30, 9, 50, 0)}));
        when(orderRepository.aggregatePaidByUsernames(anyCollection(), any()))
                .thenReturn(List.<Object[]>of(
                        new Object[] {USER_A, 4, new BigDecimal("200.00")},
                        new Object[] {USER_B, 0, BigDecimal.ZERO}));

        ViewerEntity va = viewer(USER_A, "观众A", "13312344032", "uid-union-a");
        ViewerEntity vb = viewer(USER_B, "观众B", null, null);
        when(viewerRepository.findByUsernameIn(anyCollection())).thenReturn(List.of(va, vb));

        ActivityAudienceEntity aud = new ActivityAudienceEntity();
        aud.setActivityId(ROOM_ID);
        aud.setUsername(USER_A);
        aud.setEmployeeInviterId(100L);
        when(audienceRepository.findByActivityIdAndUsernameIn(eq(ROOM_ID), anyCollection()))
                .thenReturn(List.of(aud));
        SysUserAccountEntity emp = new SysUserAccountEntity();
        emp.setId(100L);
        emp.setUserName("张三");
        when(userRepository.findAllById(anyCollection())).thenReturn(List.of(emp));

        ConsoleCommerceDetailListResponse resp = service.list(
                ROOM_ID, 1, 10, null, null, null, null, null, null, null, null);

        assertEquals(2, resp.getTotal());
        ConsoleCommerceDetailItem a = resp.getList().stream()
                .filter(r -> USER_A.equals(r.getUsername()))
                .findFirst()
                .orElseThrow();
        assertEquals("20002", a.getSpuId());
        assertEquals("商品甲新名", a.getProductName());
        assertEquals(3, a.getOrderCount());
        assertEquals(1, a.getUnpaidCount());
        assertEquals(2, a.getPaidSuccessCount());
        // 有 1030/1050/1070/1071/1080 则已支付（本例有 1030），优先于纯退款
        assertEquals(CommercePayStatus.PAID, a.getPayStatus());
        assertEquals("149.00", a.getLiveDealAmount());
        assertEquals(4, a.getTotalPaidOrderCount());
        assertEquals("200.00", a.getTotalPaidAmount());
        assertEquals(3, a.getClickCount());
        assertEquals("2026-09-30 09:50:00", a.getLastClickTime());
        assertEquals("张三", a.getFollowerName());
        assertEquals("uid-union-a", a.getUnionid());
        assertEquals("133****4032", a.getPhoneMasked());

        ConsoleCommerceDetailItem b = resp.getList().stream()
                .filter(r -> USER_B.equals(r.getUsername()))
                .findFirst()
                .orElseThrow();
        assertEquals(CommercePayStatus.UNPAID, b.getPayStatus());
        assertEquals(0, b.getClickCount());
        assertNull(b.getLastClickTime());
    }

    @Test
    void list_payStatusFilter_refundedOnlyWhenNoPaidHolding() {
        when(orderRepository.findByActivityIdWithProduct(ROOM_ID)).thenReturn(List.of(
                order(USER_A, 1L, "A", 1081, "10.00", "2026-09-30 10:00:00"),
                order(USER_B, 2L, "B", 1010, null, "2026-09-30 10:00:00")));

        ConsoleCommerceDetailListResponse refunded = service.list(
                ROOM_ID, 1, 10, null, "refunded", null, null, null, null, null, null);
        assertEquals(1, refunded.getTotal());
        assertEquals(USER_A, refunded.getList().get(0).getUsername());
        assertEquals(CommercePayStatus.REFUNDED, refunded.getList().get(0).getPayStatus());

        ConsoleCommerceDetailListResponse unpaid = service.list(
                ROOM_ID, 1, 10, null, "unpaid", null, null, null, null, null, null);
        assertEquals(1, unpaid.getTotal());
        assertEquals(USER_B, unpaid.getList().get(0).getUsername());
    }

    @Test
    void sessionInviters_distinctSorted() {
        when(audienceRepository.findDistinctEmployeeInviterIds(ROOM_ID)).thenReturn(List.of(200L, 100L));
        SysUserAccountEntity e1 = new SysUserAccountEntity();
        e1.setId(100L);
        e1.setUserName("张三");
        SysUserAccountEntity e2 = new SysUserAccountEntity();
        e2.setId(200L);
        e2.setUserName("李四");
        when(userRepository.findAllById(anyCollection())).thenReturn(List.of(e1, e2));

        ConsoleSessionInviterListResponse resp = service.sessionInviters(ROOM_ID);
        assertEquals(2, resp.getList().size());
        assertTrue(resp.getList().stream().anyMatch(i -> "张三".equals(i.getName())));
        assertTrue(resp.getList().stream().anyMatch(i -> "李四".equals(i.getName())));
    }

    @Test
    void exportCsv_containsPayStatusZh() {
        when(orderRepository.findByActivityIdWithProduct(ROOM_ID)).thenReturn(List.of(
                order(USER_A, 1L, "商品", 1010, null, "2026-09-30 10:00:00")));
        when(viewerRepository.findByUsernameIn(anyCollection()))
                .thenReturn(List.of(viewer(USER_A, "观众A", null, null)));

        byte[] csv = service.exportCsv(ROOM_ID, null, null, null, null, null, null, null, null);
        String text = new String(csv, 3, csv.length - 3, java.nio.charset.StandardCharsets.UTF_8);
        assertTrue(text.contains("未支付"));
        assertTrue(text.contains(USER_A));
    }

    private static ActivityOrderEntity order(
            String username, Long productId, String name, int status, String amount, String orderTime) {
        ActivityOrderEntity o = new ActivityOrderEntity();
        o.setUsername(username);
        o.setProductId(productId);
        o.setProductName(name);
        o.setOrderStatus(status);
        o.setPayAmount(amount == null ? null : new BigDecimal(amount));
        o.setOrderTime(LocalDateTime.parse(orderTime.replace(' ', 'T')));
        o.setActivityId(ROOM_ID);
        return o;
    }

    private static ViewerEntity viewer(String username, String nick, String phone, String unionid) {
        ViewerEntity v = new ViewerEntity();
        v.setUsername(username);
        v.setNickname(nick);
        v.setPhone(phone);
        v.setUnionid(unionid);
        return v;
    }
}
