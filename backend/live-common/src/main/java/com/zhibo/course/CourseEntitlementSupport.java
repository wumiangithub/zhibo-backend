package com.zhibo.course;

import java.time.LocalDateTime;

/** 课程学员权益到期计算（D100）。 */
public final class CourseEntitlementSupport {

    private CourseEntitlementSupport() {}

    /** 售卖类型是否按到期时间卡「已到期」。 */
    public static boolean usesExpireClock(String accessValidityType) {
        return "days".equals(accessValidityType) || "fixed".equals(accessValidityType);
    }

    /**
     * 新建学员行时的到期时间：
     * {@code days} → {@code from + N}；{@code fixed} → 课程固定时刻；否则 null。
     */
    public static LocalDateTime expireAtJoin(CourseEntity course, LocalDateTime from) {
        if (course == null) {
            return null;
        }
        String type = course.getAccessValidityType();
        if ("fixed".equals(type)) {
            return course.getAccessValidityFixedTime();
        }
        if (!"days".equals(type) || from == null) {
            return null;
        }
        Integer days = course.getAccessValidityDays();
        if (days == null || days < 1) {
            return null;
        }
        return from.plusDays(days);
    }

    /**
     * 列表计算态：invalid / expired / valid。
     * 售卖长期不算 expired；days/fixed 看行上 expire。
     */
    public static String resolveState(
            int entitlementStatus,
            String accessValidityType,
            LocalDateTime expireTime,
            LocalDateTime now) {
        if (entitlementStatus == 0) {
            return "invalid";
        }
        if (usesExpireClock(accessValidityType)
                && expireTime != null
                && now != null
                && !expireTime.isAfter(now)) {
            return "expired";
        }
        return "valid";
    }

    public static String stateLabel(String state) {
        if ("invalid".equals(state)) {
            return "已失效";
        }
        if ("expired".equals(state)) {
            return "已到期";
        }
        return "有效";
    }

    public static String acquireWayLabel(String acquireWay) {
        if ("import".equals(acquireWay)) {
            return "导入";
        }
        return "购买";
    }

    /** 到期列展示：长期 →「长期」；days/fixed 有时间则格式化，无则「—」。 */
    public static String expireDisplay(String accessValidityType, String formattedExpireOrNull) {
        if (!usesExpireClock(accessValidityType)) {
            return "长期";
        }
        if (formattedExpireOrNull == null || formattedExpireOrNull.isBlank()) {
            return "—";
        }
        return formattedExpireOrNull;
    }
}
