package com.zhibo.admin.oms;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.Map;

/**
 * 只读查询 OMS 退款明细。未配置时 {@link #isAvailable()} 为 false，调用方应跳过查询。
 */
public interface OmsRefundQuery {

    boolean isAvailable();

    /**
     * 已完成退款（{@code rs_status=1} 且 {@code refund_status=3}）按订单号
     * {@code SUM(refund_order_money)}。{@code orderNos} 为空时返回空 Map，且不访问 OMS。
     */
    Map<String, BigDecimal> sumCompletedRefundMoney(Collection<String> orderNos);
}
