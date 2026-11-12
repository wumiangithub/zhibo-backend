package com.zhibo.presence;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * D85 / D110 / D118：从微吼回调顶层 email 解析本站用户 id。不使用 {@code sender_id}。
 * <ul>
 *   <li>观众 {@code {username}@qkzb.com} → {@code {username}}</li>
 *   <li>角色（主持/嘉宾/助理）{@code e{id}@qkzb.com} → {@code e{id}}</li>
 *   <li>中控台进房 {@code z{id}@qkzb.com} → {@code z{id}}（与角色邮箱错开，避免微吼 20005）</li>
 *   <li>游客 {@code *@zhibo.local} → 本地部分</li>
 * </ul>
 */
public final class PresenceUserIds {

    public static final String QKZB_DOMAIN = "@qkzb.com";
    public static final String GUEST_DOMAIN = "@zhibo.local";

  private static final String VIEWER_USERNAME_CHARS = "[A-Za-z0-9_\\-]";
    private static final Pattern VIEWER = Pattern.compile(
            "^(" + VIEWER_USERNAME_CHARS + "{1,16})" + Pattern.quote(QKZB_DOMAIN) + "$",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern STAFF =
            Pattern.compile("^e([1-9]\\d{0,18})" + Pattern.quote(QKZB_DOMAIN) + "$", Pattern.CASE_INSENSITIVE);
    private static final Pattern CONSOLE =
            Pattern.compile("^z([1-9]\\d{0,18})" + Pattern.quote(QKZB_DOMAIN) + "$", Pattern.CASE_INSENSITIVE);
    private static final Pattern GUEST =
            Pattern.compile("^([A-Za-z0-9]{1,64})" + Pattern.quote(GUEST_DOMAIN) + "$", Pattern.CASE_INSENSITIVE);
    private static final Pattern VIEWER_USERNAME = Pattern.compile("^" + VIEWER_USERNAME_CHARS + "{1,16}$");

    private PresenceUserIds() {
    }

    /** 观众观看邮箱 {@code {username}@qkzb.com}。 */
    public static String viewerEmail(String username) {
        if (username == null || username.isBlank() || !VIEWER_USERNAME.matcher(username.trim()).matches()) {
            throw new IllegalArgumentException("username 无效");
        }
        return username.trim() + QKZB_DOMAIN;
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
     * @return 观众为 username；角色为 {@code e} + id；中控台为 {@code z} + id；游客为 guestId 本地部分。对不上返回 {@code null}
     */
    public static String parseEmail(String email) {
        if (email == null || email.isBlank()) {
            return null;
        }
        String trimmed = email.trim();
        // 员工 e*/z* 须先于观众 username（D118 后 username 可为字母数字，会误吃 e10001）
        Matcher staff = STAFF.matcher(trimmed);
        if (staff.matches()) {
            return "e" + staff.group(1);
        }
        Matcher console = CONSOLE.matcher(trimmed);
        if (console.matches()) {
            return "z" + console.group(1);
        }
        Matcher viewer = VIEWER.matcher(trimmed);
        if (viewer.matches()) {
            String local = viewer.group(1);
            if (isStaff(local) || isConsole(local)) {
                return null;
            }
            if (local.length() > 2 && local.regionMatches(true, 0, "pc", 0, 2)) {
                return null;
            }
            return local;
        }
        Matcher guest = GUEST.matcher(trimmed);
        if (guest.matches()) {
            return guest.group(1);
        }
        return null;
    }

    public static boolean isViewer(String userId) {
        return viewerUsername(userId) != null;
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
     * 仅观众 username、角色 {@code e} + id、中控台 {@code z} + id；游客本地部分等一律否。
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

    /**
     * 观众业务 username（非 {@code e*} / {@code z*} 前缀的数字员工 id）。
     *
     * @deprecated 用 {@link #viewerUsername(String)}。
     */
    @Deprecated
    public static Long viewerId(String userId) {
        String username = viewerUsername(userId);
        if (username == null) {
            return null;
        }
        try {
            return Long.parseLong(username);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    /** 若 {@code userId} 为观众 username 则返回 trim 后值，否则 {@code null}。 */
    public static String viewerUsername(String userId) {
        if (userId == null || userId.isBlank()) {
            return null;
        }
        String trimmed = userId.trim();
        if (isStaff(trimmed) || isConsole(trimmed)) {
            return null;
        }
        if (!VIEWER_USERNAME.matcher(trimmed).matches()) {
            return null;
        }
        return trimmed;
    }
}
