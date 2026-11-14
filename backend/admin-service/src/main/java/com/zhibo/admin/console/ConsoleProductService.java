package com.zhibo.admin.console;

import com.zhibo.activity.ActivityEntity;
import com.zhibo.activity.ActivityOrderRepository;
import com.zhibo.activity.ActivityProductEntity;
import com.zhibo.activity.ActivityProductId;
import com.zhibo.activity.ActivityProductRepository;
import com.zhibo.activity.ActivityRepository;
import com.zhibo.admin.activity.ActivityService;
import com.zhibo.admin.console.dto.ConsoleCartEnabledRequest;
import com.zhibo.admin.console.dto.ConsoleCartEnabledResponse;
import com.zhibo.admin.console.dto.ConsoleProductItem;
import com.zhibo.admin.console.dto.ConsoleProductListResponse;
import com.zhibo.admin.console.dto.ConsoleProductVisibleRequest;
import com.zhibo.admin.console.dto.ConsoleProductVisibleResponse;
import com.zhibo.admin.dashboard.DashboardBusinessService;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 中控台「商品」：本场绑定列表、单品显示/隐藏、小黄车总开关。
 * 封面/价等展示字段由前端直连订单中心批量详情补齐；移除走活动 update 全量覆盖 products。
 */
@Service
@RequiredArgsConstructor
public class ConsoleProductService {

    private final ActivityService activityService;
    private final ActivityRepository activityRepository;
    private final ActivityProductRepository productRepository;
    private final ActivityOrderRepository orderRepository;

    @Transactional(readOnly = true)
    public ConsoleProductListResponse list(String activityId) {
        ActivityEntity activity = activityService.requireActivity(activityId);
        String id = activity.getId();
        List<ActivityProductEntity> rows = productRepository.findByActivityIds(List.of(id));
        Map<String, Long> sold = loadSoldCounts(id);

        ConsoleProductListResponse response = new ConsoleProductListResponse();
        response.setId(id);
        response.setCartEnabled(Boolean.TRUE.equals(activity.getCartEnabled()));
        for (ActivityProductEntity row : rows) {
            String spuId = row.getId().getSpuId();
            ConsoleProductItem item = new ConsoleProductItem();
            item.setSpuId(spuId);
            item.setProductName(row.getProductName());
            item.setVisible(row.getVisible() == null || Boolean.TRUE.equals(row.getVisible()));
            item.setSoldCount(sold.getOrDefault(spuId, 0L));
            response.getList().add(item);
        }
        return response;
    }

    @Transactional
    public ConsoleProductVisibleResponse setVisible(String activityId, ConsoleProductVisibleRequest body) {
        ActivityEntity activity = activityService.requireActivity(activityId);
        if (body == null || body.getVisible() == null) {
            throw new IllegalArgumentException("visible 必填");
        }
        String spuId = body.getSpuId() == null ? "" : body.getSpuId().trim();
        if (spuId.isEmpty()) {
            throw new IllegalArgumentException("spuId 必填");
        }
        ActivityProductId key = new ActivityProductId(activity.getId(), spuId);
        ActivityProductEntity row = productRepository.findById(key)
                .orElseThrow(() -> new IllegalArgumentException("商品未绑定本场"));
        row.setVisible(body.getVisible());
        productRepository.save(row);

        ConsoleProductVisibleResponse response = new ConsoleProductVisibleResponse();
        response.setId(activity.getId());
        response.setSpuId(spuId);
        response.setVisible(Boolean.TRUE.equals(row.getVisible()));
        return response;
    }

    @Transactional
    public ConsoleCartEnabledResponse setCartEnabled(String activityId, ConsoleCartEnabledRequest body) {
        ActivityEntity activity = activityService.requireActivity(activityId);
        if (body == null || body.getEnabled() == null) {
            throw new IllegalArgumentException("enabled 必填");
        }
        activity.setCartEnabled(body.getEnabled());
        activityRepository.save(activity);

        ConsoleCartEnabledResponse response = new ConsoleCartEnabledResponse();
        response.setId(activity.getId());
        response.setCartEnabled(Boolean.TRUE.equals(activity.getCartEnabled()));
        return response;
    }

    private Map<String, Long> loadSoldCounts(String activityId) {
        Map<String, Long> map = new HashMap<>();
        for (Object[] row : orderRepository.countPaidOrdersByProduct(
                activityId, DashboardBusinessService.PAID_STATUSES)) {
            Long productId = (Long) row[0];
            if (productId == null) {
                continue;
            }
            map.put(Long.toString(productId), ((Number) row[1]).longValue());
        }
        return map;
    }
}
