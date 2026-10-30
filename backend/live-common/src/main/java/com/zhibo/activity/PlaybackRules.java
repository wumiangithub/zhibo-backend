package com.zhibo.activity;

import java.time.LocalDateTime;

/**
 * 本站回放开关 / 有效期规则（D89）。
 * 起算：{@code liveActualEndTime}；没有则计划 {@code endTime}；两者都没有则本轮不拦。
 */
public final class PlaybackRules {

    private PlaybackRules() {
    }

    /** 是否已超过「直播结束后 N 天」有效期。永久有效或无法起算 → false。 */
    public static boolean isExpired(ActivityEntity activity, LocalDateTime now) {
        if (activity == null || now == null) {
            return false;
        }
        if (!"days".equals(activity.getPlaybackValidityType())) {
            return false;
        }
        Integer days = activity.getPlaybackValidityDays();
        if (days == null || days < 1) {
            return false;
        }
        LocalDateTime from = activity.getLiveActualEndTime();
        if (from == null) {
            from = parseNullable(activity.getEndTime());
        }
        if (from == null) {
            return false;
        }
        return now.isAfter(from.plusDays(days));
    }

    /**
     * 允许开始回放观看：开启且未过期。
     *
     * @throws IllegalArgumentException 回放未开启 / 回放已过期
     */
    public static void assertReplayAllowed(ActivityEntity activity, LocalDateTime now) {
        if (Boolean.FALSE.equals(activity.getPlaybackEnabled())) {
            throw new IllegalArgumentException("回放未开启");
        }
        if (isExpired(activity, now)) {
            throw new IllegalArgumentException("回放已过期");
        }
    }

    private static LocalDateTime parseNullable(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return AttendanceTimes.parse(raw, "endTime");
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }
}
