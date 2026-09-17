package com.zhibo.vhall.util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Collection;
import java.util.Map;

/**
 * 微吼 SaaS Open API 签名工具（MD5，sign_type=0）。
 * <p>
 * 规则见 <a href="https://saas-doc.vhall.com/opendocs/show/1423">签名机制</a>：
 * 除 {@code sign} 外所有 body 参数 → 按参数名升序 → {@code 名+值} 拼接 → 首尾加 secret → MD5（小写 hex）。
 * 数组/集合只拼 key，不拼值。
 * <p>
 * 类比前端：类似给请求算一枚“防伪章”，把密钥夹在参数串两端再 hash；密钥只在服务端。
 */
public final class VhallSignUtil {

    private VhallSignUtil() {
    }

    /**
     * 生成 MD5 签名。
     *
     * @param secretKey 应用 SecretKey（环境变量 VHALL_APP_SECRET）
     * @param paramMap  参与签名的参数（含公共参数 + 业务参数，不要放 sign）
     * @return 32 位小写 MD5 hex
     */
    public static String sign(String secretKey, Map<String, ?> paramMap) {
        if (secretKey == null || secretKey.isBlank()) {
            throw new IllegalArgumentException("secretKey 不能为空");
        }
        if (paramMap == null || paramMap.isEmpty()) {
            throw new IllegalArgumentException("paramMap 不能为空");
        }

        String[] keyArray = paramMap.keySet().toArray(new String[0]);
        Arrays.sort(keyArray);

        StringBuilder source = new StringBuilder();
        source.append(secretKey);
        for (String key : keyArray) {
            if ("sign".equals(key)) {
                continue;
            }
            Object value = paramMap.get(key);
            if (value == null) {
                continue;
            }
            if (value instanceof Object[] || value instanceof Collection) {
                source.append(key);
            } else {
                source.append(key).append(value.toString().trim());
            }
        }
        source.append(secretKey);
        return md5Hex(source.toString());
    }

    static String md5Hex(String text) {
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
            // JDK 必带 MD5，这里几乎不会发生
            throw new IllegalStateException("MD5 不可用", e);
        }
    }
}
