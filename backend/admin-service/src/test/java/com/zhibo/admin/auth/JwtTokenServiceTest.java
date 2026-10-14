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
import static org.junit.jupiter.api.Assertions.assertNotNull;
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
    void issue_matchesGoBaseAdminClaims() {
        String token = service.issue(42L, "admin", "管理员", 1, "NCRM_abc", "8801");
        Claims claims = Jwts.parser()
                .verifyWith(signingKey(SECRET))
                .requireIssuer("base-admin-api")
                .requireAudience("base")
                .clock(() -> Date.from(clock.instant()))
                .build()
                .parseSignedClaims(token)
                .getPayload();
        assertEquals("42", claims.getSubject());
        assertEquals(42L, claims.get("user_id", Long.class));
        assertEquals("admin", claims.get("user_account", String.class));
        assertEquals("管理员", claims.get("user_name", String.class));
        assertEquals(1, claims.get("user_flag", Integer.class));
        assertEquals(JwtTokenService.TOKEN_TYPE_ACCESS, claims.get("token_type", String.class));
        assertEquals("NCRM_abc", claims.get("identifier", String.class));
        assertEquals("8801", claims.get("yhid", String.class));
        assertEquals("管理员", claims.get("yg_name", String.class));
        assertEquals("base-admin-api", claims.getIssuer());
        assertTrue(claims.getAudience().contains("base"));
        assertNotNull(claims.getId());
        assertNotNull(claims.getNotBefore());
        assertEquals(7200, service.expireSeconds());
        assertEquals(604800, service.refreshExpireSeconds());
    }

    @Test
    void issueRefresh_usesRefreshAudienceAndTokenType() {
        String token = service.issueRefresh(42L, "admin", "管理员", 0, "admin", "0");
        Claims claims = Jwts.parser()
                .verifyWith(signingKey(SECRET))
                .requireIssuer("base-admin-api")
                .requireAudience("base-refresh")
                .clock(() -> Date.from(clock.instant()))
                .build()
                .parseSignedClaims(token)
                .getPayload();
        assertEquals("42", claims.getSubject());
        assertTrue(claims.getAudience().contains("base-refresh"));
        assertEquals(JwtTokenService.TOKEN_TYPE_REFRESH, claims.get("token_type", String.class));
        AuthException ex = assertThrows(AuthException.class, () -> service.parseUserId(token));
        assertEquals(401, ex.getCode());
    }

    @Test
    void parseAccessAllowExpired_acceptsExpiredAccess() {
        String token = Jwts.builder()
                .issuer("base-admin-api")
                .audience().add("base").and()
                .subject("42")
                .claim("user_id", 42L)
                .claim("token_type", JwtTokenService.TOKEN_TYPE_ACCESS)
                .issuedAt(Date.from(clock.instant().minusSeconds(8000)))
                .expiration(Date.from(clock.instant().minusSeconds(60)))
                .signWith(signingKey(SECRET))
                .compact();
        assertEquals(42L, service.parseAccessAllowExpired(token));
        assertThrows(AuthException.class, () -> service.parseUserId(token));
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
        String token = service.issue(7L, "ops", "运营", 0, "ops", "7");
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
