package com.zhibo.activity;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 把已付订单按支付时间聚合成成交趋势桶。
 * <ul>
 *   <li>{@code dealAmount}：该桶内 {@code pay_amount} 求和</li>
 *   <li>{@code soldCount}：该桶内已付订单行数（销量）</li>
 * </ul>
 */
public final class TradeTrendCalculator {

    private TradeTrendCalculator() {
    }

    /**
     * @param rows 每行 {@code [payTime, payAmount]}；空金额按 0
     */
    public static List<TradeTrendPoint> aggregate(
            List<Object[]> rows,
            LocalDateTime start,
            LocalDateTime end,
            int bucketSeconds) {
        if (bucketSeconds <= 0) {
            throw new IllegalArgumentException("bucketSeconds 须为正整数");
        }
        if (!end.isAfter(start)) {
            return List.of();
        }

        Map<LocalDateTime, Bucket> buckets = new HashMap<>();
        if (rows != null) {
            for (Object[] row : rows) {
                if (row == null || row.length < 2) {
                    continue;
                }
                LocalDateTime payTime = (LocalDateTime) row[0];
                if (payTime == null || payTime.isBefore(start) || !payTime.isBefore(end)) {
                    continue;
                }
                LocalDateTime bucketStart = bucketStartOf(start, payTime, bucketSeconds);
                Bucket bucket = buckets.computeIfAbsent(bucketStart, key -> new Bucket());
                bucket.soldCount++;
                BigDecimal amount = row[1] instanceof BigDecimal bd ? bd : BigDecimal.ZERO;
                bucket.dealAmount = bucket.dealAmount.add(amount);
            }
        }

        List<TradeTrendPoint> points = new ArrayList<>();
        LocalDateTime cursor = start;
        while (cursor.isBefore(end)) {
            Bucket bucket = buckets.getOrDefault(cursor, Bucket.EMPTY);
            points.add(new TradeTrendPoint(
                    AttendanceTimes.format(cursor),
                    bucket.dealAmount,
                    bucket.soldCount));
            cursor = cursor.plusSeconds(bucketSeconds);
        }
        return points;
    }

    private static LocalDateTime bucketStartOf(
            LocalDateTime rangeStart, LocalDateTime eventTime, int bucketSeconds) {
        long secondsFromStart = java.time.Duration.between(rangeStart, eventTime).getSeconds();
        long bucketIndex = secondsFromStart / bucketSeconds;
        return rangeStart.plusSeconds(bucketIndex * bucketSeconds);
    }

    private static final class Bucket {
        static final Bucket EMPTY = new Bucket();
        BigDecimal dealAmount = BigDecimal.ZERO;
        int soldCount;
    }
}
