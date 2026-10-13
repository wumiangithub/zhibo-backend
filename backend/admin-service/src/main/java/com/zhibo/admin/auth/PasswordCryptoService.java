package com.zhibo.admin.auth;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.ByteBuffer;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Service;

/**
 * AES-256-GCM 口令加解密。
 * <p>
 * 字节布局：{@code IV(12) + ciphertext + tag(16)} → Base64。
 * 明文 JSON：{@code {"request_id":"...","password":"..."}}。
 */
@Service
public class PasswordCryptoService {

    private static final int IV_LENGTH = 12;
    private static final int TAG_BITS = 128;

    private final AuthProperties properties;
    private final ObjectMapper objectMapper;
    private final SecureRandom secureRandom = new SecureRandom();

    public PasswordCryptoService(AuthProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    public record DecryptedPassword(String requestId, String password) {}

    public DecryptedPassword decrypt(String cipherBase64) {
        if (properties.passwordAesKeyMissing()) {
            throw new IllegalArgumentException("未配置口令加密密钥");
        }
        try {
            byte[] keyBytes = Base64.getDecoder().decode(properties.getPasswordAesKeyBase64().trim());
            if (keyBytes.length != 32) {
                throw new IllegalArgumentException("口令加密密钥无效");
            }
            byte[] raw = Base64.getDecoder().decode(cipherBase64.trim());
            if (raw.length <= IV_LENGTH + 16) {
                throw new IllegalArgumentException("账号或密码错误");
            }
            byte[] iv = new byte[IV_LENGTH];
            System.arraycopy(raw, 0, iv, 0, IV_LENGTH);
            byte[] cipherAndTag = new byte[raw.length - IV_LENGTH];
            System.arraycopy(raw, IV_LENGTH, cipherAndTag, 0, cipherAndTag.length);

            SecretKey key = new SecretKeySpec(keyBytes, "AES");
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            byte[] plain = cipher.doFinal(cipherAndTag);

            JsonNode node = objectMapper.readTree(plain);
            String requestId = text(node, "request_id");
            String password = text(node, "password");
            if (requestId == null || requestId.isBlank() || password == null) {
                throw new IllegalArgumentException("账号或密码错误");
            }
            return new DecryptedPassword(requestId, password);
        } catch (IllegalArgumentException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalArgumentException("账号或密码错误");
        }
    }

    /** 单测 / 本地自检用：加密明文密码。 */
    public String encrypt(String requestId, String password) {
        try {
            byte[] keyBytes = Base64.getDecoder().decode(properties.getPasswordAesKeyBase64().trim());
            byte[] iv = new byte[IV_LENGTH];
            secureRandom.nextBytes(iv);
            SecretKey key = new SecretKeySpec(keyBytes, "AES");
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            String json = objectMapper.writeValueAsString(
                    objectMapper.createObjectNode()
                            .put("request_id", requestId)
                            .put("password", password));
            byte[] cipherAndTag = cipher.doFinal(json.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            ByteBuffer buf = ByteBuffer.allocate(iv.length + cipherAndTag.length);
            buf.put(iv);
            buf.put(cipherAndTag);
            return Base64.getEncoder().encodeToString(buf.array());
        } catch (Exception ex) {
            throw new IllegalStateException("加密失败", ex);
        }
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? null : value.asText();
    }
}
