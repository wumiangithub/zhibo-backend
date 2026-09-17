package com.zhibo.live.watch;

import com.zhibo.live.watch.dto.WatchResponse;
import com.zhibo.vhall.client.VhallClient;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * 观看鉴权：取微吼全屏嵌入地址，拼游客身份参数。
 */
@Service
@RequiredArgsConstructor
public class WatchService {

    static final String PATH_INFO = "/v3/webinars/webinar/info";
    static final String DEFAULT_NICKNAME = "观众";
    static final int NICKNAME_MAX = 50;
    private static final Pattern GUEST_ID_PATTERN = Pattern.compile("^[A-Za-z0-9]+$");

    private final VhallClient vhallClient;

    public WatchResponse watch(long activityId, String guestId, String nickname) {
        if (activityId <= 0) {
            throw new IllegalArgumentException("活动 id 无效");
        }

        String resolvedGuestId = resolveGuestId(guestId);
        String resolvedNickname = resolveNickname(nickname);

        @SuppressWarnings("unchecked")
        Map<String, Object> info = vhallClient.postForData(
                PATH_INFO, Map.of("webinar_id", activityId), Map.class);

        String baseEmbed = asString(info.get("full_embed_share_link"));
        if (baseEmbed == null || baseEmbed.isBlank()) {
            throw new IllegalStateException("活动缺少全屏嵌入地址 full_embed_share_link");
        }

        String embedUrl = UriComponentsBuilder.fromUriString(baseEmbed)
                .replaceQueryParam("email", resolvedGuestId + "@zhibo.local")
                .replaceQueryParam("nickname", resolvedNickname)
                .encode()
                .build()
                .toUriString();

        WatchResponse response = new WatchResponse();
        response.setId(activityId);
        response.setTitle(asString(info.get("subject")));
        response.setState(asInteger(info.get("webinar_state")));
        response.setType(asInteger(info.get("webinar_type")));
        response.setNickname(resolvedNickname);
        response.setGuestId(resolvedGuestId);
        response.setEmbedUrl(embedUrl);
        return response;
    }

    private static String resolveGuestId(String guestId) {
        if (guestId == null || guestId.isBlank()) {
            return UUID.randomUUID().toString().replace("-", "");
        }
        String trimmed = guestId.trim();
        if (!GUEST_ID_PATTERN.matcher(trimmed).matches()) {
            throw new IllegalArgumentException("guestId 只能包含字母和数字");
        }
        return trimmed;
    }

    private static String resolveNickname(String nickname) {
        if (nickname == null || nickname.isBlank()) {
            return DEFAULT_NICKNAME;
        }
        String trimmed = nickname.trim();
        if (trimmed.length() > NICKNAME_MAX) {
            throw new IllegalArgumentException("nickname 不能超过 " + NICKNAME_MAX + " 字");
        }
        return trimmed;
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
