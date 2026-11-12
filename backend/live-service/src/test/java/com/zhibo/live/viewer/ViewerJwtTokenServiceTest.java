package com.zhibo.live.viewer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

class ViewerJwtTokenServiceTest {

    private static final String SECRET = "b7d4e2a1-9c3f-4a6b-8f21-6d0e7c5b1a93";

    private ViewerAuthProperties properties;
    private Clock clock;
    private ViewerJwtTokenService service;

    @BeforeEach
    void setUp() {
        properties = new ViewerAuthProperties();
        properties.setJwtSecret(SECRET);
        properties.setJwtExpireSeconds(7200);
        properties.setJwtIssuer("zhibo-viewer");
        properties.setJwtAudience("viewer");
        properties.setJwtRefreshAudience("viewer-refresh");
        clock = Clock.fixed(Instant.parse("2026-11-05T04:00:00Z"), ZoneOffset.UTC);
        service = new ViewerJwtTokenService(properties, clock);
    }

    @Test
    void issueAccess_matchesGoViewersClaims_fromOauth() {
        String token = service.issueAccess("qk90001");
        Claims claims = Jwts.parser()
                .verifyWith(signingKey(SECRET))
                .requireIssuer("zhibo-viewer")
                .requireAudience("viewer")
                .clock(() -> Date.from(clock.instant()))
                .build()
                .parseSignedClaims(token)
                .getPayload();
        assertEquals("qk90001", claims.getSubject());
        assertEquals(ViewerJwtTokenService.TOKEN_TYPE_ACCESS, claims.get("token_type", String.class));
        assertEquals("qk90001", claims.get("username", String.class));
        assertEquals("qk90001", claims.get("third_user_id", String.class));
        assertEquals("zhibo-viewer", claims.getIssuer());
        assertTrue(claims.getAudience().contains("viewer"));
        assertNotNull(claims.getId());
        assertNotNull(claims.getNotBefore());
        assertEquals(7200, service.expireSeconds());
        assertEquals(604800, service.refreshExpireSeconds());
    }

    @Test
    void issueAccess_blankOmsLeavesEmptyClaims() {
        String token = service.issueAccess("u7");
        Claims claims = Jwts.parser()
                .verifyWith(signingKey(SECRET))
                .requireIssuer("zhibo-viewer")
                .requireAudience("viewer")
                .clock(() -> Date.from(clock.instant()))
                .build()
                .parseSignedClaims(token)
                .getPayload();
        assertEquals("u7", claims.get("username", String.class));
        assertEquals("u7", claims.get("third_user_id", String.class));
        assertEquals("u7", claims.getSubject());
    }

    @Test
    void issueRefresh_usesRefreshAudienceAndTokenType() {
        String token = service.issueRefresh("qk_user_x");
        Claims claims = Jwts.parser()
                .verifyWith(signingKey(SECRET))
                .requireIssuer("zhibo-viewer")
                .requireAudience("viewer-refresh")
                .clock(() -> Date.from(clock.instant()))
                .build()
                .parseSignedClaims(token)
                .getPayload();
        assertEquals("qk_user_x", claims.getSubject());
        assertTrue(claims.getAudience().contains("viewer-refresh"));
        assertEquals(ViewerJwtTokenService.TOKEN_TYPE_REFRESH, claims.get("token_type", String.class));
        assertEquals("qk_user_x", claims.get("username", String.class));
        assertEquals("qk_user_x", claims.get("third_user_id", String.class));
        ViewerAuthException ex = assertThrows(ViewerAuthException.class, () -> service.parseUserId(token));
        assertEquals(401, ex.getCode());
    }

    @Test
    void parseUserId_roundTrip() {
        String token = service.issueAccess("7");
        assertEquals("7", service.parseUsername(token));
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
