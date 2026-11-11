package com.zhibo.presence;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * D85 / D110：从微吼回调顶层 email 解析本站用户 id。不使用 {@code sender_id}。
 * <ul>
 *   <li>观众 {@code {id}@qkzb.com} → {@code {id}}</li>
 *   <li>角色（主持/嘉宾/助理）{@code e{id}@qkzb.com} → {@code e{id}}</li>
 *   <li>中控台进房 {@code z{id}@qkzb.com} → {@code z{id}}（与角色邮箱错开，避免微吼 20005）</li>
 *   <li>游客 {@code *@zhibo.local} → 本地部分</li>
 * </ul>
 */
public final class PresenceUserIds {

    public static final String QKZB_DOMAIN = "@qkzb.com";
    public static final String GUEST_DOMAIN = "@zhibo.local";

    private static final Pattern VIEWER =
            Pattern.compile("^([1-9]\\d{0,18})" + Pattern.quote(QKZB_DOMAIN) + "$", Pattern.CASE_INSENSITIVE);
    private static final Pattern STAFF =
            Pattern.compile("^e([1-9]\\d{0,18})" + Pattern.quote(QKZB_DOMAIN) + "$", Pattern.CASE_INSENSITIVE);
    private static final Pattern CONSOLE =
            Pattern.compile("^z([1-9]\\d{0,18})" + Pattern.quote(QKZB_DOMAIN) + "$", Pattern.CASE_INSENSITIVE);
    private static final Pattern GUEST =
            Pattern.compile("^([A-Za-z0-9]{1,64})" + Pattern.quote(GUEST_DOMAIN) + "$", Pattern.CASE_INSENSITIVE);

    private PresenceUserIds() {
    }

    /** 观众观看邮箱 {@code {uid}@qkzb.com}（{@code uid} 为用户中心 id）。 */
    public static String viewerEmail(long uid) {
        if (uid <= 0) {
            throw new IllegalArgumentException("uid 无效");
        }
        return uid + QKZB_DOMAIN;
    }

    /** 员工角色（主持/嘉宾/助理）邮箱 {@code e{id}@qkzb.com}。 */
    public static String staffEmail(long employeeId) {
        if (employeeId <= 0) {
            throw new IllegalArgumentException("员工 id 无效");
        }
        return "e" + employeeId + QKZB_DOMAIN;
    }

    /** 中控台进房邮箱 {@code z{id}@qkzb.com}（与角色 {@code e{id}} 错开）。 */
    public static String consoleEmail(long employeeId) {
        if (employeeId <= 0) {
            throw new IllegalArgumentException("员工 id 无效");
        }
        return "z" + employeeId + QKZB_DOMAIN;
    }

    /**
     * @return 观众为十进制 id；角色为 {@code e} + id；中控台为 {@code z} + id；游客为 guestId 本地部分。对不上返回 {@code null}
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
        Matcher console = CONSOLE.matcher(trimmed);
        if (console.matches()) {
            return "z" + console.group(1);
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

    public static boolean isConsole(String userId) {
        if (userId == null || userId.isBlank()) {
            return false;
        }
        return userId.trim().matches("(?i)z[1-9]\\d{0,18}");
    }

    /**
     * 是否允许写 {@code room_attendance} / {@code room_presence_event}。
     * 仅观众纯数字 id、角色 {@code e} + id、中控台 {@code z} + id；游客本地部分等一律否。
     */
    public static boolean mayWriteRoom(String userId) {
        return isViewer(userId) || isStaff(userId) || isConsole(userId);
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
