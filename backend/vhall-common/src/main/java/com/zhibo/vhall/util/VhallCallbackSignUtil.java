package com.zhibo.vhall.util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Collection;
import java.util.Map;

/**
 * 微吼<strong>回调</strong>签名校验（与 Open API {@link VhallSignUtil} 不同）。
 * <p>
 * 规则（经典 SaaS 回调）：去掉 {@code signature}/{@code sign} → 参数名升序 →
 * 对每个标量字段拼 {@code key|secret|value} → MD5 小写。嵌套对象 / 数组不参与签名。
 * 文档示例见 <a href="https://sdkapi.vhall.com/home/vhallapi/callback">回调签名</a>。
 */
public final class VhallCallbackSignUtil {

    private VhallCallbackSignUtil() {
    }

    public static boolean matches(String secretKey, Map<String, ?> body, String signature) {
        if (signature == null || signature.isBlank()) {
            return false;
        }
        return signature.trim().equalsIgnoreCase(sign(secretKey, body));
    }

    public static String sign(String secretKey, Map<String, ?> body) {
        if (secretKey == null || secretKey.isBlank()) {
            throw new IllegalArgumentException("callback secret 不能为空");
        }
        if (body == null || body.isEmpty()) {
            throw new IllegalArgumentException("回调体不能为空");
        }

        String[] keys = body.keySet().toArray(new String[0]);
        Arrays.sort(keys);

        StringBuilder source = new StringBuilder();
        for (String key : keys) {
            if ("signature".equals(key) || "sign".equals(key)) {
                continue;
            }
            Object value = body.get(key);
            if (value == null) {
                continue;
            }
            if (value instanceof Map || value instanceof Collection || value.getClass().isArray()) {
                continue;
            }
            source.append(key).append('|').append(secretKey).append('|').append(String.valueOf(value).trim());
        }
        return md5Hex(source.toString());
    }

    private static String md5Hex(String text) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(text.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(32);
            for (byte b : digest) {
                int i = b & 0xff;
                if (i < 16) {
                    sb.append('0');
                }
                sb.append(Integer.toHexString(i));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("MD5 不可用", e);
        }
    }
}
