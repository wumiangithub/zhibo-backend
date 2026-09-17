package com.zhibo.admin.activity;

import com.zhibo.admin.activity.dto.ActivityEmbedResponse;
import com.zhibo.vhall.client.VhallClient;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.Map;

/**
 * 管理后台嵌入：拿控制台 token，拼微吼嵌入详情页。
 */
@Service
@RequiredArgsConstructor
public class EmbedService {

    static final String PATH_GET_TOKEN = "/v3/users/open-user/get-token";
    static final String EMBED_BASE = "https://e.vhall.com/v3/embed/live/detail/";

    private final VhallClient vhallClient;

    public ActivityEmbedResponse embed(long activityId) {
        if (activityId <= 0) {
            throw new IllegalArgumentException("活动 id 无效");
        }

        @SuppressWarnings("unchecked")
        Map<String, Object> data = vhallClient.postForData(PATH_GET_TOKEN, Map.of(), Map.class);
        String token = asString(data.get("token"));
        if (token == null || token.isBlank()) {
            throw new IllegalStateException("微吼未返回授权 token");
        }

        String embedUrl = UriComponentsBuilder
                .fromUriString(EMBED_BASE + activityId)
                .queryParam("token", token)
                .encode()
                .build()
                .toUriString();

        ActivityEmbedResponse response = new ActivityEmbedResponse();
        response.setEmbedUrl(embedUrl);
        return response;
    }

    private static String asString(Object value) {
        return value == null ? null : value.toString();
    }
}
