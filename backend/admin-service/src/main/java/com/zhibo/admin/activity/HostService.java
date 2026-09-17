package com.zhibo.admin.activity;

import com.zhibo.admin.activity.dto.ActivityHostResponse;
import com.zhibo.vhall.client.VhallClient;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 主持人开播链接：微吼 GET get-role-url。
 */
@Service
@RequiredArgsConstructor
public class HostService {

    static final String PATH_ROLE_URL = "/v3/webinars/live/get-role-url";
    /** 微吼角色：1 主持人 */
    static final int ROLE_HOST = 1;

    private final VhallClient vhallClient;

    public ActivityHostResponse host(long activityId) {
        if (activityId <= 0) {
            throw new IllegalArgumentException("活动 id 无效");
        }

        Map<String, Object> params = new LinkedHashMap<>();
        params.put("webinar_id", activityId);
        params.put("type", ROLE_HOST);
        @SuppressWarnings("unchecked")
        Map<String, Object> data = vhallClient.getForData(PATH_ROLE_URL, params, Map.class);

        String hostUrl = asString(data.get("page_url"));
        if (hostUrl == null || hostUrl.isBlank()) {
            throw new IllegalStateException("微吼未返回主持人地址 page_url");
        }

        ActivityHostResponse response = new ActivityHostResponse();
        response.setHostUrl(hostUrl);
        return response;
    }

    private static String asString(Object value) {
        return value == null ? null : value.toString();
    }
}
