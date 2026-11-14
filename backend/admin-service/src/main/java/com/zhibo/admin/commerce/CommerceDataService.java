package com.zhibo.admin.commerce;

import com.zhibo.activity.ActivityEntity;
import com.zhibo.activity.ActivityOrderRepository;
import com.zhibo.activity.ActivityProductClickRepository;
import com.zhibo.activity.ActivityProductEntity;
import com.zhibo.activity.ActivityProductRepository;
import com.zhibo.admin.activity.ActivityService;
import com.zhibo.admin.commerce.dto.ActivityCommerceDataResponse;
import com.zhibo.admin.dashboard.DashboardBusinessService;
import com.zhibo.admin.oms.OmsRefundQuery;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 直播设置 · 直播带货数据：绑品全量 + 点击/成交聚合 + 成功退款金额（查询时只读 OMS 明细）。 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CommerceDataService {

    private static final AtomicBoolean OMS_MISSING_WARNED = new AtomicBoolean();

    private final ActivityService activityService;
    private final ActivityProductRepository productRepository;
    private final ActivityProductClickRepository clickRepository;
    private final ActivityOrderRepository orderRepository;
    private final OmsRefundQuery omsRefundQuery;

    @Transactional(readOnly = true)
    public ActivityCommerceDataResponse commerceData(String activityId) {
        ActivityEntity activity = activityService.requireActivity(activityId);
        String id = activity.getId();

        List<ActivityProductEntity> products = productRepository.findByActivityIds(List.of(id));
        Map<String, long[]> clicks = loadClicks(id);
        Map<String, DealAgg> deals = loadDeals(id);
        Map<String, BigDecimal> refunds = loadRefunds(id, products);

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
            row.setRefundAmount(
                    DashboardBusinessService.formatAmount(refunds.getOrDefault(spuId, BigDecimal.ZERO)));
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

    /**
     * 本场全部订单（不限成交用的已支付状态）按 {@code order_no} 查 OMS 完成退款，再按 {@code product_id} 汇总。
     * OMS 未配置或查询失败时返回空 Map（调用方显示 {@code "0.00"}），不抛错。
     */
    private Map<String, BigDecimal> loadRefunds(String activityId, List<ActivityProductEntity> products) {
        if (products == null || products.isEmpty()) {
            return Map.of();
        }
        if (!omsRefundQuery.isAvailable()) {
            if (OMS_MISSING_WARNED.compareAndSet(false, true)) {
                log.warn(
                        "OMS 数据源未配置，直播带货成功退款金额按 0.00（需 OMS_DB_HOST / OMS_DB_USER / OMS_DB_PASSWORD）");
            }
            return Map.of();
        }
        List<Object[]> orderRows = orderRepository.findOrderNoAndProductIdByActivityId(activityId);
        if (orderRows == null || orderRows.isEmpty()) {
            return Map.of();
        }
        Map<String, String> orderNoToSpu = new HashMap<>();
        List<String> orderNos = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (Object[] row : orderRows) {
            if (row == null || row.length < 2) {
                continue;
            }
            String orderNo = row[0] instanceof String s ? s.trim() : null;
            Long productId = row[1] instanceof Number n ? n.longValue() : null;
            if (orderNo == null || orderNo.isEmpty() || productId == null) {
                continue;
            }
            orderNoToSpu.put(orderNo, Long.toString(productId));
            if (seen.add(orderNo)) {
                orderNos.add(orderNo);
            }
        }
        if (orderNos.isEmpty()) {
            return Map.of();
        }
        Map<String, BigDecimal> byOrder;
        try {
            byOrder = omsRefundQuery.sumCompletedRefundMoney(orderNos);
        } catch (RuntimeException ex) {
            log.warn("查询 OMS 退款明细失败，成功退款金额按 0.00，activityId={}", activityId, ex);
            return Map.of();
        }
        if (byOrder == null || byOrder.isEmpty()) {
            return Map.of();
        }
        Map<String, BigDecimal> byProduct = new HashMap<>();
        for (Map.Entry<String, BigDecimal> e : byOrder.entrySet()) {
            String spuId = orderNoToSpu.get(e.getKey());
            BigDecimal money = e.getValue();
            if (spuId == null || money == null) {
                continue;
            }
            byProduct.merge(spuId, money, BigDecimal::add);
        }
        return byProduct;
    }

    private record DealAgg(long userCount, BigDecimal amount) {
        static final DealAgg EMPTY = new DealAgg(0L, BigDecimal.ZERO);
    }
}
