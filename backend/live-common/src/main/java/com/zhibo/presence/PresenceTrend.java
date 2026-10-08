package com.zhibo.presence;

import java.util.List;

/** 进出三线走势查询结果（watch / admin 同形）。 */
public record PresenceTrend(
        String id,
        String startTime,
        String endTime,
        int bucketSeconds,
        List<PresenceTrendPoint> points) {
}
