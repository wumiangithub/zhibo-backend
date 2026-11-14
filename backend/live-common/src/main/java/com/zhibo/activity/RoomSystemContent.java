package com.zhibo.activity;

/**
 * {@code room_comment.content} 系统消息 JSON 快照（comment_type=5；D127）。
 * 固定字段手工拼/拆，避免 live-common 依赖 jackson。
 */
public record RoomSystemContent(String text, boolean pin) {

    public String toJson() {
        StringBuilder sb = new StringBuilder(96);
        sb.append('{');
        appendString(sb, "text", text == null ? "" : text, false);
        sb.append(',');
        sb.append('"').append("pin").append('"').append(':').append(pin ? "true" : "false");
        sb.append('}');
        return sb.toString();
    }

    public RoomSystemContent withPin(boolean nextPin) {
        return new RoomSystemContent(text, nextPin);
    }

    public static RoomSystemContent parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String body = raw.trim();
        if (!body.startsWith("{") || !body.endsWith("}")) {
            return null;
        }
        String text = readString(body, "text");
        if (text == null || text.isBlank()) {
            return null;
        }
        Boolean pin = readBoolean(body, "pin");
        return new RoomSystemContent(text.trim(), pin != null && pin);
    }

    private static void appendString(StringBuilder sb, String key, String value, boolean comma) {
        if (comma) {
            sb.append(',');
        }
        sb.append('"').append(key).append('"').append(':');
        sb.append('"').append(escape(value)).append('"');
    }

    private static String escape(String value) {
        return value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    private static String readString(String json, String key) {
        String needle = "\"" + key + "\"";
        int keyAt = json.indexOf(needle);
        if (keyAt < 0) {
            return null;
        }
        int colon = json.indexOf(':', keyAt + needle.length());
        if (colon < 0) {
            return null;
        }
        int i = colon + 1;
        while (i < json.length() && Character.isWhitespace(json.charAt(i))) {
            i++;
        }
        if (i >= json.length() || json.charAt(i) != '"') {
            return null;
        }
        i++;
        StringBuilder out = new StringBuilder();
        while (i < json.length()) {
            char c = json.charAt(i++);
            if (c == '\\' && i < json.length()) {
                char n = json.charAt(i++);
                switch (n) {
                    case 'n' -> out.append('\n');
                    case 'r' -> out.append('\r');
                    case 't' -> out.append('\t');
                    case '"' -> out.append('"');
                    case '\\' -> out.append('\\');
                    default -> out.append(n);
                }
                continue;
            }
            if (c == '"') {
                return out.toString();
            }
            out.append(c);
        }
        return null;
    }

    private static Boolean readBoolean(String json, String key) {
        String needle = "\"" + key + "\"";
        int keyAt = json.indexOf(needle);
        if (keyAt < 0) {
            return null;
        }
        int colon = json.indexOf(':', keyAt + needle.length());
        if (colon < 0) {
            return null;
        }
        int i = colon + 1;
        while (i < json.length() && Character.isWhitespace(json.charAt(i))) {
            i++;
        }
        if (json.startsWith("true", i)) {
            return true;
        }
        if (json.startsWith("false", i)) {
            return false;
        }
        return null;
    }
}
