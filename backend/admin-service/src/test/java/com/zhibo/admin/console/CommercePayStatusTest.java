package com.zhibo.admin.console;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.zhibo.activity.ActivityOrderStatuses;
import java.util.List;
import org.junit.jupiter.api.Test;

class CommercePayStatusTest {

    @Test
    void derive_paidHoldingBeatsRefunded() {
        assertEquals(
                CommercePayStatus.PAID,
                CommercePayStatus.derive(
                        List.of(ActivityOrderStatuses.PAID, ActivityOrderStatuses.REFUNDED)));
        assertEquals(
                CommercePayStatus.PAID,
                CommercePayStatus.derive(List.of(ActivityOrderStatuses.REFUNDING)));
        assertEquals(
                CommercePayStatus.PAID,
                CommercePayStatus.derive(List.of(ActivityOrderStatuses.PARTIAL_REFUND)));
        assertEquals(
                CommercePayStatus.PAID,
                CommercePayStatus.derive(List.of(ActivityOrderStatuses.SIGNED)));
    }

    @Test
    void derive_refundedOnlyWhenNoPaidHolding() {
        assertEquals(
                CommercePayStatus.REFUNDED,
                CommercePayStatus.derive(
                        List.of(ActivityOrderStatuses.UNPAID, ActivityOrderStatuses.REFUNDED)));
    }

    @Test
    void derive_unpaidDefault() {
        assertEquals(
                CommercePayStatus.UNPAID,
                CommercePayStatus.derive(List.of(ActivityOrderStatuses.UNPAID)));
        assertEquals(CommercePayStatus.UNPAID, CommercePayStatus.derive(List.of()));
        assertEquals(CommercePayStatus.UNPAID, CommercePayStatus.derive(null));
    }

    @Test
    void labelZh() {
        assertEquals("已支付", CommercePayStatus.labelZh(CommercePayStatus.PAID));
        assertEquals("已退款", CommercePayStatus.labelZh(CommercePayStatus.REFUNDED));
        assertEquals("未支付", CommercePayStatus.labelZh(CommercePayStatus.UNPAID));
    }
}
