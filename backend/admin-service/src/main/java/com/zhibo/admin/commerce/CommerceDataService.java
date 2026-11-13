package com.zhibo.admin.commerce;

import com.zhibo.activity.ActivityEntity;
import com.zhibo.activity.ActivityOrderRepository;
import com.zhibo.activity.ActivityProductClickRepository;
import com.zhibo.activity.ActivityProductEntity;
import com.zhibo.activity.ActivityProductRepository;
import com.zhibo.admin.activity.ActivityService;
import com.zhibo.admin.commerce.dto.ActivityCommerceDataResponse;
import com.zhibo.admin.dashboard.DashboardBusinessService;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 直播设置 · 直播带货数据：绑品全量 + 点击/成交聚合。 */
@Service
@RequiredArgsConstructor
public class CommerceDataService {

    private final ActivityService activityService;
    private final ActivityProductRepository productRepository;
    private final ActivityProductClickRepository clickRepository;
    private final ActivityOrderRepository orderRepository;

    @Transactional(readOnly = true)
    public ActivityCommerceDataResponse commerceData(String activityId) {
        ActivityEntity activity = activityService.requireActivity(activityId);
        String id = activity.getId();

        List<ActivityProductEntity> products = productRepository.findByActivityIds(List.of(id));
        Map<String, long[]> clicks = loadClicks(id);
        Map<String, DealAgg> deals = loadDeals(id);

        ActivityCommerceDataResponse response = new ActivityCommerceDataResponse();
        response.setId(id);
        for (ActivityProductEntity product : products) {
            String spuId = product.getId().getSpuId();
            long[] click = clicks.getOrDefault(spuId, new long[] {0L, 0L});
            DealAgg deal = deals.getOrDefault(spuId, DealAgg.EMPTY);
            ActivityCommerceDataResponse.Row row = new ActivityCommerceDataResponse.Row();
            row.setSpuId(spuId);
            row.setProductName(product.getProductName());
            row.setClickCount(click[0]);
            row.setClickUserCount(click[1]);
            row.setDealUserCount(deal.userCount);
            row.setDealAmount(DashboardBusinessService.formatAmount(deal.amount));
            row.setRefundAmount("0.00");
            response.getList().add(row);
        }
        return response;
    }

    private Map<String, long[]> loadClicks(String activityId) {
        Map<String, long[]> map = new HashMap<>();
        for (Object[] row : clickRepository.aggregateBySpu(activityId)) {
            String spuId = (String) row[0];
            long count = ((Number) row[1]).longValue();
            long users = ((Number) row[2]).longValue();
            map.put(spuId, new long[] {count, users});
        }
        return map;
    }

    private Map<String, DealAgg> loadDeals(String activityId) {
        Map<String, DealAgg> map = new HashMap<>();
        for (Object[] row : orderRepository.aggregatePaidByProduct(
                activityId, DashboardBusinessService.PAID_STATUSES)) {
            Long productId = (Long) row[0];
            if (productId == null) {
                continue;
            }
            String spuId = Long.toString(productId);
            long users = ((Number) row[1]).longValue();
            BigDecimal amount = row[2] instanceof BigDecimal bd ? bd : BigDecimal.ZERO;
            map.put(spuId, new DealAgg(users, amount));
        }
        return map;
    }

    private record DealAgg(long userCount, BigDecimal amount) {
        static final DealAgg EMPTY = new DealAgg(0L, BigDecimal.ZERO);
    }
}
