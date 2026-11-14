package com.zhibo.admin.console;

import com.zhibo.activity.ActivityEntity;
import com.zhibo.activity.ActivityOrderRepository;
import com.zhibo.activity.ActivityProductClickRepository;
import com.zhibo.admin.activity.ActivityService;
import com.zhibo.admin.console.dto.ConsoleTradeStatsResponse;
import com.zhibo.admin.dashboard.DashboardBusinessService;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 中控台「交易数据」KPI：只读本站 {@code activity_order} / {@code activity_product_click}，不查 OMS。
 */
@Service
@RequiredArgsConstructor
public class ConsoleTradeStatsService {

    private static final List<Integer> UNPAID_STATUSES = List.of(1);

    private final ActivityService activityService;
    private final ActivityOrderRepository orderRepository;
    private final ActivityProductClickRepository clickRepository;

    @Transactional(readOnly = true)
    public ConsoleTradeStatsResponse stats(String activityId) {
        ActivityEntity activity = activityService.requireActivity(activityId);
        String id = activity.getId();
        var paid = DashboardBusinessService.PAID_STATUSES;

        BigDecimal amount = orderRepository.sumPayAmountByActivityAndStatuses(id, paid);
        long dealUsers = orderRepository.countDistinctUsernameByActivityAndStatuses(id, paid);
        long dealProducts = orderRepository.countByActivityAndStatuses(id, paid);
        long clickUsers = clickRepository.countDistinctUsernameByActivityId(id);
        long unpaidUsers =
                orderRepository.countDistinctUsernameByActivityAndStatuses(id, UNPAID_STATUSES);

        ConsoleTradeStatsResponse response = new ConsoleTradeStatsResponse();
        response.setId(id);
        response.setDealUserCount(dealUsers);
        response.setDealProductCount(dealProducts);
        response.setProductClickUserCount(clickUsers);
        response.setDealConversionRate(formatConversionRate(dealUsers, clickUsers));
        response.setAvgOrderAmount(formatAvgOrderAmount(amount, dealUsers));
        response.setUnpaidUserCount(unpaidUsers);
        return response;
    }

    /** 成交人数 / 点击人数 × 100，一位小数；分母 0 → "0.0"。 */
    static String formatConversionRate(long dealUsers, long clickUsers) {
        if (clickUsers <= 0) {
            return "0.0";
        }
        return BigDecimal.valueOf(dealUsers)
                .multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(clickUsers), 1, RoundingMode.HALF_UP)
                .toPlainString();
    }

    /** 成交金额 / 成交人数；分母 0 → "0.00"。 */
    static String formatAvgOrderAmount(BigDecimal amount, long dealUsers) {
        if (dealUsers <= 0) {
            return "0.00";
        }
        BigDecimal safe = amount == null ? BigDecimal.ZERO : amount;
        return safe.divide(BigDecimal.valueOf(dealUsers), 2, RoundingMode.HALF_UP).toPlainString();
    }
}
