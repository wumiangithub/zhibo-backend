package com.zhibo.activity;

import java.time.LocalDateTime;

/**
 * 售卖访问有效期（D90）。
 * {@code days} 仅在有 {@code liveActualEndTime} 后起算；未实际结束不卡天数。
 */
public final class AccessRules {

    private AccessRules() {
    }

    /** 是否已超过「直播结束后 N 天」。长期有效或尚未实际结束 → false。 */
    public static boolean isAccessExpired(ActivityEntity activity, LocalDateTime now) {
        if (activity == null || now == null) {
            return false;
        }
        if (!"days".equals(activity.getAccessValidityType())) {
            return false;
        }
        Integer days = activity.getAccessValidityDays();
        if (days == null || days < 1) {
            return false;
        }
        LocalDateTime from = activity.getLiveActualEndTime();
        if (from == null) {
            return false;
        }
        return now.isAfter(from.plusDays(days));
    }

    /**
     * 本轮是否强制售卖门禁：单独售卖且类型为加密 / 指定用户。
     * {@code free} / 存量 {@code paid} / {@code sellAlone=false} → 不拦。
     */
    public static boolean requiresGate(ActivityEntity activity) {
        if (activity == null || !Boolean.TRUE.equals(activity.getSellAlone())) {
            return false;
        }
        String sellType = activity.getSellType();
        return "encrypted".equals(sellType) || "specified".equals(sellType);
    }
}
