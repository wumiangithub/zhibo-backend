package com.zhibo.admin.console;

import com.zhibo.activity.ActivityEntity;
import com.zhibo.activity.ActivityOrderRepository;
import com.zhibo.admin.activity.ActivityService;
import com.zhibo.admin.console.dto.ConsoleWatchTradeResponse;
import com.zhibo.admin.dashboard.DashboardBusinessService;
import java.math.BigDecimal;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 中控台「观看数据」成交汇总：只读本站 {@code activity_order}，不查 OMS。
 */
@Service
@RequiredArgsConstructor
public class ConsoleWatchTradeService {

    private final ActivityService activityService;
    private final ActivityOrderRepository orderRepository;

    @Transactional(readOnly = true)
    public ConsoleWatchTradeResponse watchTrade(String activityId) {
        ActivityEntity activity = activityService.requireActivity(activityId);
        String id = activity.getId();
        var statuses = DashboardBusinessService.PAID_STATUSES;

        BigDecimal amount = orderRepository.sumPayAmountByActivityAndStatuses(id, statuses);
        long users = orderRepository.countDistinctUsernameByActivityAndStatuses(id, statuses);
        long products = orderRepository.countByActivityAndStatuses(id, statuses);

        ConsoleWatchTradeResponse response = new ConsoleWatchTradeResponse();
        response.setId(id);
        response.setDealAmount(DashboardBusinessService.formatAmount(amount));
        response.setDealUserCount(users);
        response.setDealProductCount(products);
        return response;
    }
}
