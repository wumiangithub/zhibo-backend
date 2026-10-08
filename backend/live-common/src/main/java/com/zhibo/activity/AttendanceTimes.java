package com.zhibo.activity;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;

/**
 * 进出时间：只接受微吼推送的 {@code yyyy-MM-dd HH:mm:ss}，不换成服务器当前时间。
 */
public final class AttendanceTimes {

    private static final DateTimeFormatter PARSER = DateTimeFormatter
            .ofPattern("uuuu-MM-dd HH:mm:ss")
            .withResolverStyle(ResolverStyle.STRICT);
    private static final DateTimeFormatter PRINTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private AttendanceTimes() {
    }

    public static LocalDateTime parse(String raw, String field) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException(field + " 不能为空");
        }
        try {
            return LocalDateTime.parse(raw.trim(), PARSER);
        } catch (DateTimeParseException ex) {
            throw new IllegalArgumentException(field + " 格式须为 yyyy-MM-dd HH:mm:ss");
        }
    }

    public static String format(LocalDateTime time) {
        return time.format(PRINTER);
    }
}
