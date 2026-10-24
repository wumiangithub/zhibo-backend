package com.zhibo.common;

import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * 本站业务 UUID（D74）：32 位小写十六进制、无横线；库 VARCHAR(32)。
 * guestId / 微吼 request-id / TOS session 不走本类。
 */
public final class UuidIds {

    private static final Pattern HEX_32 = Pattern.compile("[0-9a-f]{32}");

    private UuidIds() {
    }

    /** 新业务主键。 */
    public static String newId() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    /**
     * 校验并归一化路径/请求中的业务 UUID。
     * 接受 32 位无横线，或带横线的标准形式（旧书签）；一律返回 32 位小写。
     */
    public static String require(String id) {
        return require(id, "id 无效", "须为本站业务 UUID（32 位十六进制）");
    }

    public static String require(String id, String emptyMsg, String invalidMsg) {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException(emptyMsg);
        }
        String normalized = id.trim().replace("-", "").toLowerCase(Locale.ROOT);
        if (!HEX_32.matcher(normalized).matches()) {
            throw new IllegalArgumentException(invalidMsg);
        }
        return normalized;
    }
}
