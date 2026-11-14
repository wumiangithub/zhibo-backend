package com.zhibo.admin.console;

import com.zhibo.activity.ActivityOrderStatuses;
import java.util.Collection;

/**
 * 带货明细支付状态三态派生（D124）。
 * 库 {@code order_status} 为 OMS 码：见 {@link ActivityOrderStatuses}。
 */
public final class CommercePayStatus {

    public static final String UNPAID = "unpaid";
    public static final String PAID = "paid";
    public static final String REFUNDED = "refunded";

    private CommercePayStatus() {}

    /**
     * 优先级：存在已付持有态（1030/1050/1070/1071/1080）→ paid；
     * 否则存在 1081 → refunded；否则 unpaid。
     */
    public static String derive(Collection<Integer> statuses) {
        boolean hasPaidHolding = false;
        boolean hasFullRefund = false;
        if (statuses != null) {
            for (Integer status : statuses) {
                if (ActivityOrderStatuses.isPaidHolding(status)) {
                    hasPaidHolding = true;
                } else if (ActivityOrderStatuses.isFullRefund(status)) {
                    hasFullRefund = true;
                }
            }
        }
        if (hasPaidHolding) {
            return PAID;
        }
        if (hasFullRefund) {
            return REFUNDED;
        }
        return UNPAID;
    }

    public static String labelZh(String code) {
        if (PAID.equals(code)) {
            return "已支付";
        }
        if (REFUNDED.equals(code)) {
            return "已退款";
        }
        return "未支付";
    }
}
