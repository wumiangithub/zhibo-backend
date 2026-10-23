package com.zhibo.live.viewer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Date;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class ViewerAuthInterceptorTest {

    private static final String SECRET = "viewer-test-secret-0123456789abcdef";

    private ViewerAuthInterceptor interceptor;
    private ViewerJwtTokenService jwtTokenService;
    private Clock clock;

    @BeforeEach
    void setUp() {
        ViewerAuthProperties properties = new ViewerAuthProperties();
        properties.setJwtSecret(SECRET);
        properties.setJwtExpireSeconds(7200);
        properties.setJwtIssuer("zhibo-viewer");
        properties.setJwtAudience("viewer");
        properties.setJwtRefreshAudience("viewer-refresh");
        clock = Clock.fixed(Instant.parse("2026-10-23T02:00:00Z"), ZoneOffset.UTC);
        jwtTokenService = new ViewerJwtTokenService(properties, clock);
        interceptor = new ViewerAuthInterceptor(jwtTokenService, new ObjectMapper());
    }

    @Test
    void watchAndCallback_areNotTreatedAsLoggedOut() throws Exception {
        assertAllowed("GET", "/api/v1/watch/activities");
        assertAllowed("GET", "/api/v1/watch/detail/abc");
        assertAllowed("POST", "/api/v1/callbacks/vhall/presence");
    }

    @Test
    void refreshTokenAsBearer_rejectedOnMe() throws Exception {
        String refresh = jwtTokenService.issueRefresh(8L);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/viewer/auth/me");
        request.addHeader("Authorization", "Bearer " + refresh);
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertFalse(interceptor.preHandle(request, response, new Object()));
        assertEquals(200, response.getStatus());
        assertTrue(response.getContentAsString().contains("\"code\":401"));
    }

    @Test
    void adminAudience_rejected() throws Exception {
        String adminLike = Jwts.builder()
                .issuer("zhibo-viewer")
                .audience().add("base").and()
                .subject("1")
                .claim("token_type", ViewerJwtTokenService.TOKEN_TYPE_ACCESS)
                .issuedAt(Date.from(clock.instant()))
                .expiration(Date.from(clock.instant().plusSeconds(7200)))
                .signWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8)))
                .compact();
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/viewer/auth/me");
        request.addHeader("Authorization", "Bearer " + adminLike);
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertFalse(interceptor.preHandle(request, response, new Object()));
        assertTrue(response.getContentAsString().contains("\"code\":401"));
    }

    @Test
    void whitelist_allowsLoginAndRefreshWithoutBearer() throws Exception {
        assertAllowed("POST", "/api/v1/viewer/auth/wechat-login");
        assertAllowed("POST", "/api/v1/viewer/auth/phone-login");
        assertAllowed("POST", "/api/v1/viewer/auth/refresh");

        MockHttpServletRequest me = new MockHttpServletRequest("GET", "/api/v1/viewer/auth/me");
        MockHttpServletResponse response = new MockHttpServletResponse();
        assertFalse(interceptor.preHandle(me, response, new Object()));
        assertTrue(response.getContentAsString().contains("\"code\":401"));
    }

    private void assertAllowed(String method, String path) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest(method, path);
        MockHttpServletResponse response = new MockHttpServletResponse();
        assertTrue(interceptor.preHandle(request, response, new Object()));
        assertFalse(response.getContentAsString().contains("\"code\":401"));
    }
}
