package com.zhibo.admin.console;

import com.zhibo.activity.ActivityEntity;
import com.zhibo.activity.ActivityOrderRepository;
import com.zhibo.activity.AttendanceTimes;
import com.zhibo.activity.TradeTrendCalculator;
import com.zhibo.activity.TradeTrendPoint;
import com.zhibo.admin.activity.ActivityService;
import com.zhibo.admin.console.dto.ConsoleTradeTrendPointResponse;
import com.zhibo.admin.console.dto.ConsoleTradeTrendResponse;
import com.zhibo.admin.dashboard.DashboardBusinessService;
import com.zhibo.presence.PresenceTrendService;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 中控台「交易数据」成交趋势：按支付时间桶聚合本场已付订单金额与销量。
 */
@Service
@RequiredArgsConstructor
public class ConsoleTradeTrendService {

    private final ActivityService activityService;
    private final ActivityOrderRepository orderRepository;

    @Transactional(readOnly = true)
    public ConsoleTradeTrendResponse trend(
            String activityId, String startTime, String endTime, Integer bucketSeconds) {
        ActivityEntity activity = activityService.requireActivity(activityId);
        LocalDateTime start = AttendanceTimes.parse(startTime, "startTime");
        LocalDateTime end = AttendanceTimes.parse(endTime, "endTime");
        if (end.isBefore(start)) {
            throw new IllegalArgumentException("endTime 不能早于 startTime");
        }
        if (Duration.between(start, end).compareTo(Duration.ofDays(1)) > 0) {
            throw new IllegalArgumentException("查询跨度不能超过 1 天");
        }
        int bucket =
                bucketSeconds == null
                        ? PresenceTrendService.DEFAULT_BUCKET_SECONDS
                        : bucketSeconds;
        if (bucket != PresenceTrendService.DEFAULT_BUCKET_SECONDS) {
            throw new IllegalArgumentException("bucketSeconds 本迭代仅支持 60");
        }

        List<Object[]> rows = orderRepository.findPaidPayTimeAndAmount(
                activity.getId(), DashboardBusinessService.PAID_STATUSES, start, end);
        List<TradeTrendPoint> points = TradeTrendCalculator.aggregate(rows, start, end, bucket);

        ConsoleTradeTrendResponse response = new ConsoleTradeTrendResponse();
        response.setId(activity.getId());
        response.setStartTime(AttendanceTimes.format(start));
        response.setEndTime(AttendanceTimes.format(end));
        response.setBucketSeconds(bucket);
        response.setPoints(toResponses(points));
        return response;
    }

    private static List<ConsoleTradeTrendPointResponse> toResponses(List<TradeTrendPoint> points) {
        List<ConsoleTradeTrendPointResponse> list = new ArrayList<>(points.size());
        for (TradeTrendPoint point : points) {
            ConsoleTradeTrendPointResponse item = new ConsoleTradeTrendPointResponse();
            item.setTime(point.time());
            item.setDealAmount(DashboardBusinessService.formatAmount(point.dealAmount()));
            item.setSoldCount(point.soldCount());
            list.add(item);
        }
        return list;
    }
}
