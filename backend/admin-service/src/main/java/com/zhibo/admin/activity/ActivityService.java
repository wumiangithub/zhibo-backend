package com.zhibo.admin.activity;

import com.zhibo.admin.activity.dto.ActivityDetailResponse;
import com.zhibo.admin.activity.dto.ActivityListResponse;
import com.zhibo.admin.activity.dto.ActivitySummary;
import com.zhibo.admin.activity.dto.CreateActivityRequest;
import com.zhibo.admin.activity.dto.CreateActivityResponse;
import com.zhibo.admin.activity.dto.DeleteActivityResponse;
import com.zhibo.admin.activity.dto.EndActivityResponse;
import com.zhibo.admin.activity.dto.UpdateActivityRequest;
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
    /** 已联调：改标题 / 开始时间 */
    static final String PATH_EDIT = "/v3/webinars/webinar/edit";
    /** 已联调：结束直播（预告也可调，状态变 3） */
    static final String PATH_END = "/v3/webinars/live/end";
    /** 已联调：参数名是 webinar_ids（复数），可传单个 id 字符串 */
    static final String PATH_DELETE = "/v3/webinars/webinar/delete";

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
    public static String toVhallStartTime(String startTime) {
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
        requirePositiveId(id);
        Map<String, Object> params = Map.of("webinar_id", id);
        @SuppressWarnings("unchecked")
        Map<String, Object> data = vhallClient.postForData(PATH_INFO, params, Map.class);
        return mapDetail(data);
    }

    /**
     * 改活动：只传要改的字段。微吼 {@code PATH_EDIT}，时间裁到分钟。
     */
    public CreateActivityResponse update(long id, UpdateActivityRequest request) {
        requirePositiveId(id);
        if (request == null) {
            throw new IllegalArgumentException("请求体不能为空");
        }
        boolean hasTitle = request.getTitle() != null && !request.getTitle().isBlank();
        boolean hasStart = request.getStartTime() != null && !request.getStartTime().isBlank();
        if (!hasTitle && !hasStart) {
            throw new IllegalArgumentException("title 与 startTime 至少填一个");
        }

        Map<String, Object> params = new LinkedHashMap<>();
        params.put("webinar_id", id);
        if (hasTitle) {
            params.put("subject", request.getTitle().trim());
        }
        if (hasStart) {
            params.put("start_time", toVhallStartTime(request.getStartTime()));
        }
        vhallClient.postForData(PATH_EDIT, params, Map.class);
        return new CreateActivityResponse(id);
    }

    /**
     * 结束直播。微吼 {@code PATH_END} 后拉详情取最新 state。
     */
    public EndActivityResponse end(long id) {
        requirePositiveId(id);
        vhallClient.postForData(PATH_END, Map.of("webinar_id", id), Map.class);

        @SuppressWarnings("unchecked")
        Map<String, Object> info = vhallClient.postForData(
                PATH_INFO, Map.of("webinar_id", id), Map.class);
        Integer state = asInteger(info.get("webinar_state"));
        return new EndActivityResponse(id, state);
    }

    /**
     * 删除活动。微吼要 {@code webinar_ids}（复数），这里传单个 id。
     */
    public DeleteActivityResponse delete(long id) {
        requirePositiveId(id);
        vhallClient.postForData(PATH_DELETE, Map.of("webinar_ids", String.valueOf(id)), Map.class);
        return new DeleteActivityResponse(id);
    }

    private static void requirePositiveId(long id) {
        if (id <= 0) {
            throw new IllegalArgumentException("活动 id 无效");
        }
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
        // 微吼未结束时 end_time 常为 0000-00-00…；约定不输出该字段（jackson non_null）
        String endTime = asString(raw.get("end_time"));
        if (endTime != null && !endTime.startsWith("0000")) {
            detail.setEndTime(endTime);
        }
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
