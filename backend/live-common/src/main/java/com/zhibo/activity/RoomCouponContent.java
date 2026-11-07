package com.zhibo.activity;

/**
 * {@code room_comment.content} 优惠券 JSON 快照（comment_type=3；D113）。
 * 固定字段手工拼/拆，避免 live-common 依赖 jackson。
 * 发送人部门来自岗位表链路，不写 {@code qw_bmxx}。
 */
public record RoomCouponContent(
        Long couponId,
        String couponNo,
        String couponName,
        String couponAmount,
        String couponStartDate,
        String couponEndDate,
        Long employeeId,
        Long deptId,
        String deptName) {

    public String toJson() {
        StringBuilder sb = new StringBuilder(320);
        sb.append('{');
        appendLong(sb, "couponId", couponId, false);
        appendString(sb, "couponNo", couponNo, true);
        appendString(sb, "couponName", couponName, true);
        appendString(sb, "couponAmount", couponAmount, true);
        appendString(sb, "couponStartDate", couponStartDate, true);
        appendString(sb, "couponEndDate", couponEndDate, true);
        if (employeeId != null) {
            appendLong(sb, "employeeId", employeeId, true);
        }
        if (deptId != null) {
            appendLong(sb, "deptId", deptId, true);
        }
        if (deptName != null && !deptName.isBlank()) {
            appendString(sb, "deptName", deptName, true);
        }
        sb.append('}');
        return sb.toString();
    }

    public static RoomCouponContent parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String text = raw.trim();
        if (!text.startsWith("{") || !text.endsWith("}")) {
            return null;
        }
        Long couponId = readLong(text, "couponId");
        if (couponId == null || couponId <= 0) {
            return null;
        }
        String couponName = readString(text, "couponName");
        String couponAmount = readString(text, "couponAmount");
        if (isBlank(couponName) || isBlank(couponAmount)) {
            return null;
        }
        return new RoomCouponContent(
                couponId,
                readString(text, "couponNo"),
                couponName,
                couponAmount,
                readString(text, "couponStartDate"),
                readString(text, "couponEndDate"),
                readLong(text, "employeeId"),
                readLong(text, "deptId"),
                readString(text, "deptName"));
    }

    private static void appendLong(StringBuilder sb, String key, Long value, boolean comma) {
        if (comma) {
            sb.append(',');
        }
        sb.append('"').append(key).append('"').append(':').append(value);
    }

    private static void appendString(StringBuilder sb, String key, String value, boolean comma) {
        if (comma) {
            sb.append(',');
        }
        sb.append('"').append(key).append('"').append(':');
        if (value == null) {
            sb.append("null");
            return;
        }
        sb.append('"').append(escape(value)).append('"');
    }

    private static String escape(String value) {
        StringBuilder out = new StringBuilder(value.length() + 8);
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '\\' -> out.append("\\\\");
                case '"' -> out.append("\\\"");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> out.append(c);
            }
        }
        return out.toString();
    }

    private static Long readLong(String json, String key) {
        String token = extractRaw(json, key);
        if (token == null || "null".equals(token)) {
            return null;
        }
        try {
            return Long.parseLong(token.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String readString(String json, String key) {
        String token = extractRaw(json, key);
        if (token == null || "null".equals(token)) {
            return null;
        }
        String t = token.trim();
        if (t.length() >= 2 && t.charAt(0) == '"' && t.charAt(t.length() - 1) == '"') {
            return unescape(t.substring(1, t.length() - 1));
        }
        return null;
    }

    /** 粗解析：按 {@code "key":} 取到下一个未转义逗号或右花括号。 */
    private static String extractRaw(String json, String key) {
        String needle = "\"" + key + "\"";
        int idx = json.indexOf(needle);
        if (idx < 0) {
            return null;
        }
        int colon = json.indexOf(':', idx + needle.length());
        if (colon < 0) {
            return null;
        }
        int i = colon + 1;
        while (i < json.length() && Character.isWhitespace(json.charAt(i))) {
            i++;
        }
        if (i >= json.length()) {
            return null;
        }
        if (json.charAt(i) == '"') {
            StringBuilder sb = new StringBuilder();
            sb.append('"');
            i++;
            boolean esc = false;
            while (i < json.length()) {
                char c = json.charAt(i++);
                sb.append(c);
                if (esc) {
                    esc = false;
                    continue;
                }
                if (c == '\\') {
                    esc = true;
                    continue;
                }
                if (c == '"') {
                    break;
                }
            }
            return sb.toString();
        }
        int start = i;
        while (i < json.length()) {
            char c = json.charAt(i);
            if (c == ',' || c == '}') {
                break;
            }
            i++;
        }
        return json.substring(start, i);
    }

    private static String unescape(String value) {
        StringBuilder out = new StringBuilder(value.length());
        boolean esc = false;
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (esc) {
                out.append(switch (c) {
                    case 'n' -> '\n';
                    case 'r' -> '\r';
                    case 't' -> '\t';
                    default -> c;
                });
                esc = false;
                continue;
            }
            if (c == '\\') {
                esc = true;
                continue;
            }
            out.append(c);
        }
        return out.toString();
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
