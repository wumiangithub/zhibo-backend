package com.zhibo.activity;

import java.util.List;
import java.util.Set;

/**
 * {@code activity_order.order_status} 与 OMS 状态码对齐（原样落库，不再映射 1～5）。
 *
 * <p>OMS 码：1010 待支付 / 1020 部分支付 / 1030 已支付 / 1040 撞单 / 1050 已签约 /
 * 1060 已取消 / 1070 分期退款中 / 1071 退款中 / 1080 部分退款 / 1081 已退款（另有 1015/1041）。
 */
public final class ActivityOrderStatuses {

    public static final int UNPAID = 1010;
    public static final int PAY_CONFIRMING = 1015;
    public static final int PARTIAL_PAID = 1020;
    public static final int PAID = 1030;
    public static final int CONFLICT = 1040;
    public static final int CONFLICT_CLEARED = 1041;
    public static final int SIGNED = 1050;
    public static final int CANCELLED = 1060;
    public static final int INSTALLMENT_REFUNDING = 1070;
    public static final int REFUNDING = 1071;
    public static final int PARTIAL_REFUND = 1080;
    public static final int REFUNDED = 1081;

    /** 回调允许写入的 OMS 状态码全集。 */
    public static final Set<Integer> KNOWN = Set.of(
            UNPAID,
            PAY_CONFIRMING,
            PARTIAL_PAID,
            PAID,
            CONFLICT,
            CONFLICT_CLEARED,
            SIGNED,
            CANCELLED,
            INSTALLMENT_REFUNDING,
            REFUNDING,
            PARTIAL_REFUND,
            REFUNDED);

    /**
     * 已付（成交 / soldCount / 经营数据等）：历史上收过款或已签约，含退款流转中与已退款行
     *（与旧映射 2/3/4/5 同口径，并补 1050/1070）。
     */
    public static final List<Integer> PAID_STATUSES = List.of(
            PAID,
            SIGNED,
            INSTALLMENT_REFUNDING,
            REFUNDING,
            PARTIAL_REFUND,
            REFUNDED);

    /** 未支付计数：仅待支付 1010（不含 1020 部分支付）。 */
    public static final List<Integer> UNPAID_STATUSES = List.of(UNPAID);

    /**
     * D124 行标「已支付」持有态：有钱/签约/退款中/部分退款；不含纯已退款 1081。
     * （旧映射 2/3/4 + 1050/1070）
     */
    public static final Set<Integer> PAID_HOLDING = Set.of(
            PAID,
            SIGNED,
            INSTALLMENT_REFUNDING,
            REFUNDING,
            PARTIAL_REFUND);

    private ActivityOrderStatuses() {}

    public static boolean isKnown(int omsStatus) {
        return KNOWN.contains(omsStatus);
    }

    public static boolean isPaid(Integer status) {
        return status != null && PAID_STATUSES.contains(status);
    }

    public static boolean isUnpaid(Integer status) {
        return status != null && status == UNPAID;
    }

    public static boolean isPaidHolding(Integer status) {
        return status != null && PAID_HOLDING.contains(status);
    }

    public static boolean isFullRefund(Integer status) {
        return status != null && status == REFUNDED;
    }
}
