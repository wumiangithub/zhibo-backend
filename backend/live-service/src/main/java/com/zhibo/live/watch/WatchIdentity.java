package com.zhibo.live.watch;

import org.springframework.web.util.UriComponentsBuilder;

import java.util.UUID;
import java.util.regex.Pattern;

/**
 * 观看端身份与嵌入地址拼装：{@code watch} 与 {@code sdk} 两接口共用，避免逻辑分叉。
 * <p>
 * 类比前端抽出的纯工具函数：不依赖任何 Spring Bean，方便单测与复用。
 */
final class WatchIdentity {

    static final String DEFAULT_NICKNAME = "观众";
    static final int NICKNAME_MAX = 50;
    static final String EMAIL_DOMAIN = "@zhibo.local";
    private static final Pattern GUEST_ID_PATTERN = Pattern.compile("^[A-Za-z0-9]+$");

    private WatchIdentity() {
    }

    /** 不传则生成去横线 UUID；只允许字母数字。 */
    static String resolveGuestId(String guestId) {
        if (guestId == null || guestId.isBlank()) {
            return UUID.randomUUID().toString().replace("-", "");
        }
        String trimmed = guestId.trim();
        if (!GUEST_ID_PATTERN.matcher(trimmed).matches()) {
            throw new IllegalArgumentException("guestId 只能包含字母和数字");
        }
        return trimmed;
    }

    /** 空白默认「观众」；超长拒绝。 */
    static String resolveNickname(String nickname) {
        if (nickname == null || nickname.isBlank()) {
            return DEFAULT_NICKNAME;
        }
        String trimmed = nickname.trim();
        if (trimmed.length() > NICKNAME_MAX) {
            throw new IllegalArgumentException("nickname 不能超过 " + NICKNAME_MAX + " 字");
        }
        return trimmed;
    }

    /** SDK 与嵌入页统一的 email：{@code {guestId}@zhibo.local}。 */
    static String emailFor(String guestId) {
        return guestId + EMAIL_DOMAIN;
    }

    /** 在微吼全屏嵌入地址上拼 email / nickname，供 iframe 兜底。 */
    static String buildEmbedUrl(String baseEmbed, String guestId, String nickname) {
        return UriComponentsBuilder.fromUriString(baseEmbed)
                .replaceQueryParam("email", emailFor(guestId))
                .replaceQueryParam("nickname", nickname)
                .encode()
                .build()
                .toUriString();
    }

    /**
     * 微吼未结束时 {@code end_time} 常为 {@code 0000-00-00…}；约定不输出该字段。
     * 有效结束时间原样返回（jackson {@code non_null} 会省略 null）。
     */
    static String resolveEndTime(Object rawEndTime) {
        if (rawEndTime == null) {
            return null;
        }
        String endTime = rawEndTime.toString().trim();
        if (endTime.isEmpty() || endTime.startsWith("0000")) {
            return null;
        }
        return endTime;
    }
}
