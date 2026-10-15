package com.zhibo.admin.activity;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Service;

/**
 * 售卖加密密码 AES-256-GCM：{@code IV(12) + ciphertext + tag} → Base64。
 * 明文为 6 位数字，不走登录口令那套 JSON 包装。
 */
@Service
public class SellPasswordCryptoService {

    private static final int IV_LENGTH = 12;
    private static final int TAG_BITS = 128;

    private final SellPasswordProperties properties;
    private final SecureRandom secureRandom = new SecureRandom();

    public SellPasswordCryptoService(SellPasswordProperties properties) {
        this.properties = properties;
    }

    public String encrypt(String plain) {
        requireKey();
        try {
            byte[] keyBytes = decodeKey();
            byte[] iv = new byte[IV_LENGTH];
            secureRandom.nextBytes(iv);
            SecretKey key = new SecretKeySpec(keyBytes, "AES");
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            byte[] cipherAndTag = cipher.doFinal(plain.getBytes(StandardCharsets.UTF_8));
            ByteBuffer buf = ByteBuffer.allocate(iv.length + cipherAndTag.length);
            buf.put(iv);
            buf.put(cipherAndTag);
            return Base64.getEncoder().encodeToString(buf.array());
        } catch (IllegalArgumentException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalStateException("售卖密码加密失败", ex);
        }
    }

    public String decrypt(String cipherBase64) {
        requireKey();
        try {
            byte[] keyBytes = decodeKey();
            byte[] raw = Base64.getDecoder().decode(cipherBase64.trim());
            if (raw.length <= IV_LENGTH + 16) {
                throw new IllegalArgumentException("售卖密码解密失败");
            }
            byte[] iv = new byte[IV_LENGTH];
            System.arraycopy(raw, 0, iv, 0, IV_LENGTH);
            byte[] cipherAndTag = new byte[raw.length - IV_LENGTH];
            System.arraycopy(raw, IV_LENGTH, cipherAndTag, 0, cipherAndTag.length);
            SecretKey key = new SecretKeySpec(keyBytes, "AES");
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            return new String(cipher.doFinal(cipherAndTag), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalArgumentException("售卖密码解密失败");
        }
    }

    private void requireKey() {
        if (properties.keyMissing()) {
            throw new IllegalArgumentException("未配置售卖密码加密密钥");
        }
    }

    private byte[] decodeKey() {
        byte[] keyBytes = Base64.getDecoder().decode(properties.getSellPasswordAesKeyBase64().trim());
        if (keyBytes.length != 32) {
            throw new IllegalArgumentException("售卖密码加密密钥无效");
        }
        return keyBytes;
    }
}
