package com.zhibo.admin.auth;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Date;
import javax.crypto.SecretKey;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JwtTokenServiceTest {

    private static final String SECRET = "ae0536f9-1234-5678-8e13-5a19ed505da0";

    private AuthProperties properties;
    private Clock clock;
    private JwtTokenService service;

    @BeforeEach
    void setUp() {
        properties = new AuthProperties();
        properties.setJwtSecret(SECRET);
        properties.setJwtExpireSeconds(7200);
        properties.setJwtIssuer("base-admin-api");
        properties.setJwtAudience("base");
        clock = Clock.fixed(Instant.parse("2026-10-13T04:00:00Z"), ZoneOffset.UTC);
        service = new JwtTokenService(properties, clock);
    }

    @Test
    void issue_includesIssuerAndAudience() {
        String token = service.issue(42L, "admin");
        Claims claims = Jwts.parser()
                .verifyWith(signingKey(SECRET))
                .requireIssuer("base-admin-api")
                .requireAudience("base")
                .clock(() -> Date.from(clock.instant()))
                .build()
                .parseSignedClaims(token)
                .getPayload();
        assertEquals("42", claims.getSubject());
        assertEquals("admin", claims.get("userAccount", String.class));
        assertEquals("base-admin-api", claims.getIssuer());
        assertTrue(claims.getAudience().contains("base"));
        assertEquals(7200, service.expireSeconds());
    }

    @Test
    void parseUserId_rejectsWrongAudience() {
        String token = Jwts.builder()
                .issuer("base-admin-api")
                .audience().add("other").and()
                .subject("1")
                .issuedAt(Date.from(clock.instant()))
                .expiration(Date.from(clock.instant().plusSeconds(7200)))
                .signWith(signingKey(SECRET))
                .compact();
        AuthException ex = assertThrows(AuthException.class, () -> service.parseUserId(token));
        assertEquals(401, ex.getCode());
    }

    @Test
    void parseUserId_rejectsWrongIssuer() {
        String token = Jwts.builder()
                .issuer("other-api")
                .audience().add("base").and()
                .subject("1")
                .issuedAt(Date.from(clock.instant()))
                .expiration(Date.from(clock.instant().plusSeconds(7200)))
                .signWith(signingKey(SECRET))
                .compact();
        AuthException ex = assertThrows(AuthException.class, () -> service.parseUserId(token));
        assertEquals(401, ex.getCode());
    }

    @Test
    void parseUserId_roundTrip() {
        String token = service.issue(7L, "ops");
        assertEquals(7L, service.parseUserId(token));
    }

    private static SecretKey signingKey(String secret) {
        byte[] bytes = secret.getBytes(StandardCharsets.UTF_8);
        if (bytes.length < 32) {
            byte[] padded = new byte[32];
            System.arraycopy(bytes, 0, padded, 0, bytes.length);
            bytes = padded;
        }
        return Keys.hmacShaKeyFor(bytes);
    }
}
