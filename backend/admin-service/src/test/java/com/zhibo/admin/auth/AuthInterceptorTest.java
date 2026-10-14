package com.zhibo.admin.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AuthInterceptorTest {

    private AuthInterceptor interceptor;
    private JwtTokenService jwtTokenService;

    @BeforeEach
    void setUp() {
        AuthProperties properties = new AuthProperties();
        properties.setJwtSecret("ae0536f9-1234-5678-8e13-5a19ed505da0");
        properties.setJwtExpireSeconds(7200);
        properties.setJwtIssuer("base-admin-api");
        properties.setJwtAudience("base");
        Clock clock = Clock.fixed(Instant.parse("2026-10-13T04:00:00Z"), ZoneOffset.UTC);
        jwtTokenService = new JwtTokenService(properties, clock);
        interceptor = new AuthInterceptor(jwtTokenService, new ObjectMapper());
    }

    @Test
    void whitelist_allowsWithoutToken() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/auth/captcha");
        MockHttpServletResponse response = new MockHttpServletResponse();
        assertTrue(interceptor.preHandle(request, response, new Object()));
    }

    @Test
    void businessApi_withoutToken_returns401Json() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/activities");
        MockHttpServletResponse response = new MockHttpServletResponse();
        assertFalse(interceptor.preHandle(request, response, new Object()));
        assertEquals(200, response.getStatus());
        assertTrue(response.getContentAsString().contains("\"code\":401"));
    }

    @Test
    void businessApi_withValidToken_ok() throws Exception {
        String token = jwtTokenService.issue(1L, "admin");
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/materials");
        request.addHeader("Authorization", "Bearer " + token);
        MockHttpServletResponse response = new MockHttpServletResponse();
        assertTrue(interceptor.preHandle(request, response, new Object()));
        assertEquals(1L, request.getAttribute(AuthInterceptor.ATTR_USER_ID));
    }

    @Test
    void me_notWhitelisted() {
        assertFalse(AuthInterceptor.isWhitelisted("/api/auth/me"));
        assertTrue(AuthInterceptor.isWhitelisted("/api/auth/login"));
        assertTrue(AuthInterceptor.isWhitelisted("/api/auth/refresh"));
    }

    @Test
    void businessApi_rejectsRefreshToken() throws Exception {
        String refresh = jwtTokenService.issueRefresh(1L, "admin");
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/activities");
        request.addHeader("Authorization", "Bearer " + refresh);
        MockHttpServletResponse response = new MockHttpServletResponse();
        assertFalse(interceptor.preHandle(request, response, new Object()));
        assertTrue(response.getContentAsString().contains("\"code\":401"));
    }
}
