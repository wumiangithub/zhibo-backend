package com.zhibo.common;

import java.util.regex.Pattern;

/**
 * 观看 / 中控台 SDK 签名用的头像 URL（D111）。
 * <p>
 * <strong>有无真实头像都拼</strong> {@code ?v=}（已有 {@code v} 则覆盖），取值与进房 email 本地部分一致：
 * 观众 {@code {uid}}、中控台 {@code z{员工id}}、角色 {@code e{员工id}}（若走同类签名）。
 * 无真实头像时用默认底座再拼 query。
 * <p>
 * 进房签名另有 {@code account} = 第三方用户唯一标识（同本地部分）；聊天回包未必带回 account，
 * 认人不能只靠它，仍要靠 email / 头像 {@code ?v=}。
 */
public final class SdkAvatarHeads {

    /**
     * 默认头像底座（无 query）。可用环境变量 {@code ZHIBO_DEFAULT_AVATAR_URL} /
     * 配置 {@code zhibo.default-avatar-url} 覆盖。
     */
    public static final String PLACEHOLDER_BASE_URL =
            "https://qklive-testcos.cjs99.com/viewer/avatar/default-avatar.png";

    /** 与 PresenceUserIds / email 本地部分 / sdk.account 一致：观众数字、{@code z*}/{@code e*}。 */
    private static final Pattern IDENTITY =
            Pattern.compile("^(?:[1-9]\\d{0,18}|[ze][1-9]\\d{0,18})$", Pattern.CASE_INSENSITIVE);

    private SdkAvatarHeads() {
    }

    /** 观众身份：{@code v={uid}}。 */
    public static String resolveForViewer(String avatarUrl, long uid, String defaultBaseUrl) {
        if (uid <= 0) {
            throw new IllegalArgumentException("uid 无效");
        }
        return resolveForSign(avatarUrl, Long.toString(uid), defaultBaseUrl);
    }

    /** 中控台身份：{@code v=z{员工id}}（与 {@code z{id}@qkzb.com} / account {@code z{id}} 一致）。 */
    public static String resolveForConsole(String avatarUrl, long employeeId, String defaultBaseUrl) {
        if (employeeId <= 0) {
            throw new IllegalArgumentException("员工 id 无效");
        }
        return resolveForSign(avatarUrl, "z" + employeeId, defaultBaseUrl);
    }

    /** 角色身份：{@code v=e{员工id}}（与 {@code e{id}@qkzb.com} 一致；若走同类签名）。 */
    public static String resolveForStaff(String avatarUrl, long employeeId, String defaultBaseUrl) {
        if (employeeId <= 0) {
            throw new IllegalArgumentException("员工 id 无效");
        }
        return resolveForSign(avatarUrl, "e" + employeeId, defaultBaseUrl);
    }

    /**
     * @param avatarUrl      已有头像；空白则用默认底座，再一律拼 {@code ?v=}
     * @param identityLocal  与 email 本地部分 / sdk.account 一致：{@code 12} / {@code z276} / {@code e276}
     * @param defaultBaseUrl 默认底座；空则回退 {@link #PLACEHOLDER_BASE_URL}
     */
    public static String resolveForSign(String avatarUrl, String identityLocal, String defaultBaseUrl) {
        String identity = requireIdentity(identityLocal);
        String own = trimToNull(avatarUrl);
        if (own != null) {
            return withUserQuery(own, identity);
        }
        return withUserQuery(defaultBaseUrl, identity);
    }

    /**
     * @deprecated 请用 {@link #resolveForViewer}；保留给旧调用，等同观众数字 id。
     */
    @Deprecated
    public static String resolveForSign(String avatarUrl, long userId, String defaultBaseUrl) {
        return resolveForViewer(avatarUrl, userId, defaultBaseUrl);
    }

    /** 在 URL 后拼 {@code ?v=} / {@code &v=}（已有 {@code v} 则替换）。 */
    public static String withUserQuery(String baseUrl, String identityLocal) {
        String identity = requireIdentity(identityLocal);
        String base = trimToNull(baseUrl);
        if (base == null) {
            base = PLACEHOLDER_BASE_URL;
        }
        String withoutFrag = stripFragment(base);
        String cleaned = stripVParam(withoutFrag);
        char sep = cleaned.indexOf('?') >= 0 ? '&' : '?';
        return cleaned + sep + "v=" + identity;
    }

    /** @deprecated 请用 {@link #withUserQuery(String, String)}。 */
    @Deprecated
    public static String withUserQuery(String baseUrl, long userId) {
        if (userId <= 0) {
            throw new IllegalArgumentException("userId 无效");
        }
        return withUserQuery(baseUrl, Long.toString(userId));
    }

    private static String requireIdentity(String identityLocal) {
        if (identityLocal == null || identityLocal.isBlank()) {
            throw new IllegalArgumentException("identity 无效");
        }
        String trimmed = identityLocal.trim();
        if (!IDENTITY.matcher(trimmed).matches()) {
            throw new IllegalArgumentException("identity 无效");
        }
        // z/e 统一小写，与 PresenceUserIds email 本地部分一致
        char c0 = trimmed.charAt(0);
        if (c0 == 'Z' || c0 == 'E' || c0 == 'z' || c0 == 'e') {
            return Character.toLowerCase(c0) + trimmed.substring(1);
        }
        return trimmed;
    }

    private static String stripFragment(String url) {
        int hash = url.indexOf('#');
        return hash >= 0 ? url.substring(0, hash) : url;
    }

    /** 去掉已有 {@code v=} 查询参数（保留其它 query）。 */
    static String stripVParam(String url) {
        int q = url.indexOf('?');
        if (q < 0) {
            return url;
        }
        String path = url.substring(0, q);
        String query = url.substring(q + 1);
        if (query.isEmpty()) {
            return path;
        }
        StringBuilder kept = new StringBuilder();
        for (String part : query.split("&")) {
            if (part.isEmpty()) {
                continue;
            }
            int eq = part.indexOf('=');
            String key = eq >= 0 ? part.substring(0, eq) : part;
            if ("v".equalsIgnoreCase(key)) {
                continue;
            }
            if (kept.length() > 0) {
                kept.append('&');
            }
            kept.append(part);
        }
        if (kept.length() == 0) {
            return path;
        }
        return path + "?" + kept;
    }

    private static String trimToNull(String raw) {
        if (raw == null) {
            return null;
        }
        String t = raw.trim();
        return t.isEmpty() ? null : t;
    }
}
