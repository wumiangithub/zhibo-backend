package com.zhibo.admin.activity;

import com.zhibo.admin.activity.dto.ActivityDetailResponse;
import com.zhibo.admin.activity.dto.ActivityListResponse;
import com.zhibo.admin.activity.dto.ActivitySummary;
import com.zhibo.admin.activity.dto.CreateActivityRequest;
import com.zhibo.admin.activity.dto.CreateActivityResponse;
import com.zhibo.vhall.client.VhallClient;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 活动业务：把我们的 activity 语义转成微吼 webinar 调用。
 * <p>
 * 类比前端 service 层：Controller 不管签名和微吼字段名。
 */
@Service
@RequiredArgsConstructor
public class ActivityService {

    static final String PATH_CREATE = "/v3/webinars/webinar/create";
    static final String PATH_LIST = "/v3/webinars/webinar/get-list";
    static final String PATH_INFO = "/v3/webinars/webinar/info";

    private final VhallClient vhallClient;

    public CreateActivityResponse create(CreateActivityRequest request) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("subject", request.getTitle().trim());
        params.put("start_time", toVhallStartTime(request.getStartTime()));
        params.put("webinar_type", request.getType());

        @SuppressWarnings("unchecked")
        Map<String, Object> data = vhallClient.postForData(PATH_CREATE, params, Map.class);
        Long id = asLong(data.get("webinar_id"));
        if (id == null) {
            throw new IllegalStateException("微吼未返回 webinar_id");
        }
        return new CreateActivityResponse(id);
    }

    /**
     * 对外契约是 {@code yyyy-MM-dd HH:mm:ss}；微吼 create 只要 {@code Y-m-d H:i}（到分钟）。
     */
    static String toVhallStartTime(String startTime) {
        if (startTime == null || startTime.isBlank()) {
            throw new IllegalArgumentException("开始时间不能为空");
        }
        String value = startTime.trim();
        if (value.matches("\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}")) {
            return value.substring(0, 16);
        }
        if (value.matches("\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}")) {
            return value;
        }
        throw new IllegalArgumentException("开始时间格式须为 yyyy-MM-dd HH:mm:ss");
    }

    public ActivityListResponse list(int page, int pageSize, String keyword, Integer state) {
        if (page < 0) {
            throw new IllegalArgumentException("page 不能小于 0");
        }
        if (pageSize < 1 || pageSize > 100) {
            throw new IllegalArgumentException("pageSize 需在 1~100");
        }

        Map<String, Object> params = new LinkedHashMap<>();
        params.put("pos", page);
        params.put("limit", pageSize);
        if (keyword != null && !keyword.isBlank()) {
            params.put("title", keyword.trim());
        }
        if (state != null) {
            params.put("webinar_state", String.valueOf(state));
        }

        @SuppressWarnings("unchecked")
        Map<String, Object> data = vhallClient.postForData(PATH_LIST, params, Map.class);
        ActivityListResponse response = new ActivityListResponse();
        response.setTotal(asLong(data.get("total")) == null ? 0L : asLong(data.get("total")));
        response.setList(mapList(data.get("list")));
        return response;
    }

    public ActivityDetailResponse detail(long id) {
        if (id <= 0) {
            throw new IllegalArgumentException("活动 id 无效");
        }
        Map<String, Object> params = Map.of("webinar_id", id);
        @SuppressWarnings("unchecked")
        Map<String, Object> data = vhallClient.postForData(PATH_INFO, params, Map.class);
        return mapDetail(data);
    }

    private static List<ActivitySummary> mapList(Object rawList) {
        List<ActivitySummary> result = new ArrayList<>();
        if (!(rawList instanceof List<?> list)) {
            return result;
        }
        for (Object item : list) {
            if (item instanceof Map<?, ?> map) {
                result.add(mapSummary(cast(map)));
            }
        }
        return result;
    }

    private static ActivitySummary mapSummary(Map<String, Object> raw) {
        ActivitySummary summary = new ActivitySummary();
        summary.setId(firstLong(raw, "webinar_id", "id"));
        summary.setTitle(asString(raw.get("subject")));
        summary.setStartTime(asString(raw.get("start_time")));
        summary.setCoverUrl(asString(raw.get("img_url")));
        summary.setState(asInteger(raw.get("webinar_state")));
        summary.setType(asInteger(raw.get("webinar_type")));
        summary.setShareLink(asString(raw.get("share_link")));
        summary.setCreatedAt(asString(raw.get("created_at")));
        return summary;
    }

    private static ActivityDetailResponse mapDetail(Map<String, Object> raw) {
        ActivityDetailResponse detail = new ActivityDetailResponse();
        ActivitySummary base = mapSummary(raw);
        detail.setId(base.getId());
        detail.setTitle(base.getTitle());
        detail.setStartTime(base.getStartTime());
        detail.setCoverUrl(base.getCoverUrl());
        detail.setState(base.getState());
        detail.setType(base.getType());
        detail.setShareLink(base.getShareLink());
        detail.setCreatedAt(base.getCreatedAt());
        detail.setIntroduction(asString(raw.get("introduction")));
        detail.setEndTime(asString(raw.get("end_time")));
        detail.setVerify(asInteger(raw.get("verify")));
        detail.setPv(asInteger(raw.get("pv")));
        detail.setFullEmbedShareLink(asString(raw.get("full_embed_share_link")));
        return detail;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> cast(Map<?, ?> map) {
        return (Map<String, Object>) map;
    }

    private static Long firstLong(Map<String, Object> raw, String... keys) {
        for (String key : keys) {
            Long value = asLong(raw.get(key));
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private static Long asLong(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        try {
            return Long.parseLong(value.toString());
        } catch (NumberFormatException e) {
            return null;
        }
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

    private static String asString(Object value) {
        return value == null ? null : value.toString();
    }
}
