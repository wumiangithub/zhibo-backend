package com.zhibo.presence;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * D85：从微吼回调顶层 email 解析本站用户 id。不使用 {@code sender_id}。
 */
public final class PresenceUserIds {

    public static final String QKZB_DOMAIN = "@qkzb.com";
    public static final String GUEST_DOMAIN = "@zhibo.local";

    private static final Pattern VIEWER =
            Pattern.compile("^([1-9]\\d{0,18})" + Pattern.quote(QKZB_DOMAIN) + "$", Pattern.CASE_INSENSITIVE);
    private static final Pattern STAFF =
            Pattern.compile("^e([1-9]\\d{0,18})" + Pattern.quote(QKZB_DOMAIN) + "$", Pattern.CASE_INSENSITIVE);
    private static final Pattern GUEST =
            Pattern.compile("^([A-Za-z0-9]{1,64})" + Pattern.quote(GUEST_DOMAIN) + "$", Pattern.CASE_INSENSITIVE);

    private PresenceUserIds() {
    }

    /** 观众观看邮箱 {@code {id}@qkzb.com}。 */
    public static String viewerEmail(long viewerId) {
        if (viewerId <= 0) {
            throw new IllegalArgumentException("viewerId 无效");
        }
        return viewerId + QKZB_DOMAIN;
    }

    /** 员工开播邮箱 {@code e{id}@qkzb.com}。 */
    public static String staffEmail(long employeeId) {
        if (employeeId <= 0) {
            throw new IllegalArgumentException("员工 id 无效");
        }
        return "e" + employeeId + QKZB_DOMAIN;
    }

    /**
     * @return 观众为十进制 id；员工为 {@code e} + id；游客为 guestId 本地部分。对不上返回 {@code null}
     */
    public static String parseEmail(String email) {
        if (email == null || email.isBlank()) {
            return null;
        }
        String trimmed = email.trim();
        Matcher viewer = VIEWER.matcher(trimmed);
        if (viewer.matches()) {
            return viewer.group(1);
        }
        Matcher staff = STAFF.matcher(trimmed);
        if (staff.matches()) {
            return "e" + staff.group(1);
        }
        Matcher guest = GUEST.matcher(trimmed);
        if (guest.matches()) {
            return guest.group(1);
        }
        return null;
    }

    public static boolean isViewer(String userId) {
        return viewerId(userId) != null;
    }

    public static boolean isStaff(String userId) {
        if (userId == null || userId.isBlank()) {
            return false;
        }
        return userId.trim().matches("(?i)e[1-9]\\d{0,18}");
    }

    /**
     * 是否允许写 {@code room_attendance} / {@code room_presence_event}。
     * 仅观众纯数字 id 与员工 {@code e} + id；游客本地部分等一律否。
     */
    public static boolean mayWriteRoom(String userId) {
        return isViewer(userId) || isStaff(userId);
    }

    /** 邮箱是否为游客 {@code *@zhibo.local}。 */
    public static boolean isGuestEmail(String email) {
        if (email == null || email.isBlank()) {
            return false;
        }
        return GUEST.matcher(email.trim()).matches();
    }

    public static Long viewerId(String userId) {
        if (userId == null || userId.isBlank()) {
            return null;
        }
        String trimmed = userId.trim();
        if (!trimmed.matches("[1-9]\\d{0,18}")) {
            return null;
        }
        try {
            return Long.parseLong(trimmed);
        } catch (NumberFormatException ex) {
            return null;
        }
    }
}
