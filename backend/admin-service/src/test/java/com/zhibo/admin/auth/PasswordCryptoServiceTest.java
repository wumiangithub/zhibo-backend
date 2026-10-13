package com.zhibo.admin.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Base64;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PasswordCryptoServiceTest {

    private PasswordCryptoService service;

    @BeforeEach
    void setUp() {
        AuthProperties properties = new AuthProperties();
        byte[] key = new byte[32];
        for (int i = 0; i < 32; i++) {
            key[i] = (byte) i;
        }
        properties.setPasswordAesKeyBase64(Base64.getEncoder().encodeToString(key));
        service = new PasswordCryptoService(properties, new ObjectMapper());
    }

    @Test
    void roundTrip() {
        String requestId = UUID.randomUUID().toString();
        String cipher = service.encrypt(requestId, "secret-pass");
        PasswordCryptoService.DecryptedPassword decrypted = service.decrypt(cipher);
        assertEquals(requestId, decrypted.requestId());
        assertEquals("secret-pass", decrypted.password());
    }

    @Test
    void badCipher_throws400Style() {
        assertThrows(IllegalArgumentException.class, () -> service.decrypt("not-valid-base64!!!"));
    }
}
