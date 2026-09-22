package com.zhibo.admin.stats;

import com.zhibo.admin.stats.dto.ActivityStatsResponse;
import com.zhibo.admin.stats.dto.OnlineTrendResponse;
import com.zhibo.vhall.client.VhallClient;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 观看数据：概览 + 在线趋势。
 */
@Service
public class StatsService {

    static final String PATH_INFO = "/v3/webinars/webinar/info";
    static final String PATH_ONLINE_NOW = "/v3/data-center/webinar/current-online-number";
    static final String PATH_ONLINE_TREND = "/v3/data-center/report/online";

    static final Duration ONLINE_CACHE_TTL = Duration.ofSeconds(60);

    private static final DateTimeFormatter API_TIME =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final VhallClient vhallClient;
    private final Clock clock;
    private final ConcurrentHashMap<Long, CachedOnline> onlineCache = new ConcurrentHashMap<>();

    public StatsService(VhallClient vhallClient, Clock clock) {
        this.vhallClient = vhallClient;
        this.clock = clock;
    }

    public ActivityStatsResponse summary(long activityId) {
        requirePositiveId(activityId);

        @SuppressWarnings("unchecked")
        Map<String, Object> info = vhallClient.postForData(
                PATH_INFO, Map.of("webinar_id", activityId), Map.class);

        ActivityStatsResponse response = new ActivityStatsResponse();
        response.setId(activityId);
        response.setTitle(asString(info.get("subject")));
        response.setState(asInteger(info.get("webinar_state")));
        response.setType(asInteger(info.get("webinar_type")));
        response.setPv(asInteger(info.get("pv")));
        response.setOnlineCount(currentOnline(activityId));
        return response;
    }

    public OnlineTrendResponse onlineTrend(long activityId, String startTime, String endTime) {
        requirePositiveId(activityId);
        LocalDateTime start = parseApiTime(startTime, "startTime");
        LocalDateTime end = parseApiTime(endTime, "endTime");
        if (end.isBefore(start)) {
            throw new IllegalArgumentException("endTime 不能早于 startTime");
        }
        if (Duration.between(start, end).compareTo(Duration.ofDays(1)) > 0) {
            throw new IllegalArgumentException("查询跨度不能超过 1 天");
        }

        // 创建活动只要到分钟；趋势接口要 yyyy-MM-dd HH:mm:ss，裁掉秒会报「格式有误」
        String vhallStart = start.format(API_TIME);
        String vhallEnd = end.format(API_TIME);

        Map<String, Object> params = new LinkedHashMap<>();
        params.put("webinar_id", activityId);
        params.put("start_time", vhallStart);
        params.put("end_time", vhallEnd);

        @SuppressWarnings("unchecked")
        Map<String, Object> data = vhallClient.postForData(PATH_ONLINE_TREND, params, Map.class);

        OnlineTrendResponse response = new OnlineTrendResponse();
        response.setId(activityId);
        response.setStartTime(start.format(API_TIME));
        response.setEndTime(end.format(API_TIME));

        Object rawList = data.get("list");
        if (rawList instanceof List<?> list) {
            for (Object item : list) {
                if (!(item instanceof Map<?, ?> map)) {
                    continue;
                }
                OnlineTrendResponse.OnlinePoint point = new OnlineTrendResponse.OnlinePoint();
                point.setTime(asString(map.get("time")));
                point.setCount(asInteger(map.get("total")));
                response.getPoints().add(point);
            }
        }
        return response;
    }

    /**
     * 同一活动约 60 秒内复用上次当前在线，避免轮询打爆微吼。
     */
    Integer currentOnline(long activityId) {
        Instant now = clock.instant();
        CachedOnline cached = onlineCache.get(activityId);
        if (cached != null && Duration.between(cached.fetchedAt(), now).compareTo(ONLINE_CACHE_TTL) < 0) {
            return cached.count();
        }

        @SuppressWarnings("unchecked")
        Map<String, Object> online = vhallClient.postForData(
                PATH_ONLINE_NOW, Map.of("webinar_id", activityId), Map.class);
        Integer num = asInteger(online.get("num"));
        if (num != null) {
            onlineCache.put(activityId, new CachedOnline(num, now));
        }
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

    private static void requirePositiveId(long activityId) {
        if (activityId <= 0) {
            throw new IllegalArgumentException("活动 id 无效");
        }
    }

    private static String asString(Object value) {
        return value == null ? null : value.toString();
    }

    private static Integer asInteger(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return Integer.parseInt(value.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private record CachedOnline(int count, Instant fetchedAt) {
    }
}
