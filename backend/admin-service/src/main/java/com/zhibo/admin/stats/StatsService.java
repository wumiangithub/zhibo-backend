package com.zhibo.admin.stats;

import com.zhibo.admin.stats.dto.ActivityStatsResponse;
import com.zhibo.admin.stats.dto.OnlineTrendResponse;
import com.zhibo.vhall.client.VhallClient;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 观看数据：概览 + 在线趋势。
 */
@Service
@RequiredArgsConstructor
public class StatsService {

    static final String PATH_INFO = "/v3/webinars/webinar/info";
    static final String PATH_ONLINE_NOW = "/v3/data-center/webinar/current-online-number";
    static final String PATH_ONLINE_TREND = "/v3/data-center/report/online";

    private final VhallClient vhallClient;

    public ActivityStatsResponse summary(long activityId) {
        requirePositiveId(activityId);

        @SuppressWarnings("unchecked")
        Map<String, Object> info = vhallClient.postForData(
                PATH_INFO, Map.of("webinar_id", activityId), Map.class);

        @SuppressWarnings("unchecked")
        Map<String, Object> online = vhallClient.postForData(
                PATH_ONLINE_NOW, Map.of("webinar_id", activityId), Map.class);

        ActivityStatsResponse response = new ActivityStatsResponse();
        response.setId(activityId);
        response.setTitle(asString(info.get("subject")));
        response.setState(asInteger(info.get("webinar_state")));
        response.setType(asInteger(info.get("webinar_type")));
        response.setPv(asInteger(info.get("pv")));
        response.setOnlineCount(asInteger(online.get("num")));
        return response;
    }

    public OnlineTrendResponse onlineTrend(long activityId, String startTime, String endTime) {
        requirePositiveId(activityId);
        if (startTime == null || startTime.isBlank()) {
            throw new IllegalArgumentException("startTime 不能为空");
        }
        if (endTime == null || endTime.isBlank()) {
            throw new IllegalArgumentException("endTime 不能为空");
        }

        Map<String, Object> params = new LinkedHashMap<>();
        params.put("webinar_id", activityId);
        params.put("start_time", startTime.trim());
        params.put("end_time", endTime.trim());

        @SuppressWarnings("unchecked")
        Map<String, Object> data = vhallClient.postForData(PATH_ONLINE_TREND, params, Map.class);

        OnlineTrendResponse response = new OnlineTrendResponse();
        response.setId(activityId);
        response.setStartTime(startTime.trim());
        response.setEndTime(endTime.trim());

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
}
