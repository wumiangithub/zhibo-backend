package com.zhibo.admin.stats;

import com.zhibo.activity.ActivityEntity;
import com.zhibo.admin.activity.ActivityService;
import com.zhibo.admin.stats.dto.ActivityStatsResponse;
import com.zhibo.admin.stats.dto.OnlineTrendResponse;
import com.zhibo.liveprovider.LiveProvider;
import com.zhibo.liveprovider.LiveProviderRegistry;
import com.zhibo.liveprovider.dto.OnlinePoint;
import com.zhibo.liveprovider.dto.ProviderRoomSnapshot;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class StatsService {

    static final Duration ONLINE_CACHE_TTL = Duration.ofSeconds(60);

    private static final DateTimeFormatter API_TIME =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final ActivityService activityService;
    private final LiveProviderRegistry providerRegistry;
    private final Clock clock;
    private final ConcurrentHashMap<String, CachedOnline> onlineCache = new ConcurrentHashMap<>();

    public StatsService(
            ActivityService activityService, LiveProviderRegistry providerRegistry, Clock clock) {
        this.activityService = activityService;
        this.providerRegistry = providerRegistry;
        this.clock = clock;
    }

    public ActivityStatsResponse summary(String activityId) {
        ActivityEntity entity = activityService.requireActivity(activityId);
        LiveProvider provider = providerRegistry.require(entity.getProvider());
        ProviderRoomSnapshot info = provider.fetchRoom(entity.getProviderRoomId());

        ActivityStatsResponse response = new ActivityStatsResponse();
        response.setId(entity.getId());
        response.setTitle(entity.getTitle());
        response.setState(info.state() != null ? info.state() : entity.getState());
        response.setType(entity.getType());
        response.setPv(info.pv());
        response.setOnlineCount(currentOnline(entity));
        return response;
    }

    public OnlineTrendResponse onlineTrend(String activityId, String startTime, String endTime) {
        ActivityEntity entity = activityService.requireActivity(activityId);
        LocalDateTime start = parseApiTime(startTime, "startTime");
        LocalDateTime end = parseApiTime(endTime, "endTime");
        if (end.isBefore(start)) {
            throw new IllegalArgumentException("endTime 不能早于 startTime");
        }
        if (Duration.between(start, end).compareTo(Duration.ofDays(1)) > 0) {
            throw new IllegalArgumentException("查询跨度不能超过 1 天");
        }

        String vhallStart = start.format(API_TIME);
        String vhallEnd = end.format(API_TIME);
        LiveProvider provider = providerRegistry.require(entity.getProvider());
        List<OnlinePoint> points = provider.onlineTrend(
                entity.getProviderRoomId(), vhallStart, vhallEnd);

        OnlineTrendResponse response = new OnlineTrendResponse();
        response.setId(entity.getId());
        response.setStartTime(vhallStart);
        response.setEndTime(vhallEnd);
        for (OnlinePoint point : points) {
            OnlineTrendResponse.OnlinePoint row = new OnlineTrendResponse.OnlinePoint();
            row.setTime(point.time());
            row.setCount(point.count());
            response.getPoints().add(row);
        }
        return response;
    }

    Integer currentOnline(ActivityEntity entity) {
        Instant now = clock.instant();
        CachedOnline cached = onlineCache.get(entity.getId());
        if (cached != null && Duration.between(cached.fetchedAt(), now).compareTo(ONLINE_CACHE_TTL) < 0) {
            return cached.count();
        }
        LiveProvider provider = providerRegistry.require(entity.getProvider());
        int num = provider.currentOnline(entity.getProviderRoomId());
        onlineCache.put(entity.getId(), new CachedOnline(num, now));
        return num;
    }

    private static LocalDateTime parseApiTime(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " 不能为空");
        }
        try {
            return LocalDateTime.parse(value.trim(), API_TIME);
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException(field + " 格式须为 yyyy-MM-dd HH:mm:ss");
        }
    }

    private record CachedOnline(int count, Instant fetchedAt) {
    }
}
