package com.zhibo.presence;

import com.zhibo.activity.ActivityEntity;
import com.zhibo.activity.ActivityRepository;
import com.zhibo.activity.AttendanceTimes;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * 进出三线走势。watch / admin 共用。
 */
@Service
@RequiredArgsConstructor
public class PresenceTrendService {

    public static final int DEFAULT_BUCKET_SECONDS = 60;

    private final ActivityRepository activityRepository;
    private final PresenceEventRepository eventRepository;

    @Transactional(readOnly = true)
    public PresenceTrend trend(String activityId, String startTime, String endTime, Integer bucketSeconds) {
        ActivityEntity activity = requireActivity(activityId);
        LocalDateTime start = AttendanceTimes.parse(startTime, "startTime");
        LocalDateTime end = AttendanceTimes.parse(endTime, "endTime");
        if (end.isBefore(start)) {
            throw new IllegalArgumentException("endTime 不能早于 startTime");
        }
        if (Duration.between(start, end).compareTo(Duration.ofDays(1)) > 0) {
            throw new IllegalArgumentException("查询跨度不能超过 1 天");
        }
        int bucket = bucketSeconds == null ? DEFAULT_BUCKET_SECONDS : bucketSeconds;
        if (bucket != DEFAULT_BUCKET_SECONDS) {
            throw new IllegalArgumentException("bucketSeconds 本迭代仅支持 60");
        }

        List<PresenceEventEntity> events = eventRepository
                .findByRoomIdAndEventTimeGreaterThanEqualAndEventTimeLessThanOrderByEventTimeAsc(
                        activity.getId(), start, end);
        List<PresenceTrendPoint> points = PresenceTrendCalculator.aggregate(events, start, end, bucket);
        return new PresenceTrend(
                activity.getId(),
                AttendanceTimes.format(start),
                AttendanceTimes.format(end),
                bucket,
                points);
    }

    private ActivityEntity requireActivity(String id) {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("活动 id 无效");
        }
        String uuid;
        try {
            uuid = UUID.fromString(id.trim()).toString();
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("活动 id 须为本站 UUID");
        }
        return activityRepository.findById(uuid)
                .orElseThrow(() -> new IllegalArgumentException("活动不存在"));
    }
}
