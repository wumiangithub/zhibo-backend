package com.zhibo.admin.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhibo.admin.web.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.nio.charset.StandardCharsets;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * admin 业务 API Bearer 鉴权。白名单：登录流程接口（不含 {@code /api/v1/auth/me}），
 * 以及对外前缀 {@code /api/v1/outside/**}（当前不鉴权，域名白名单后续再做）。
 */
@Component
public class AuthInterceptor implements HandlerInterceptor {

    public static final String ATTR_USER_ID = "authUserId";

    private final JwtTokenService jwtTokenService;
    private final ObjectMapper objectMapper;

    public AuthInterceptor(JwtTokenService jwtTokenService, ObjectMapper objectMapper) {
        this.jwtTokenService = jwtTokenService;
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws Exception {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }
        String path = request.getRequestURI();
        if (isWhitelisted(path)) {
            return true;
        }
        String header = request.getHeader("Authorization");
        String token = extractBearer(header);
        try {
            long userId = jwtTokenService.parseUserId(token);
            request.setAttribute(ATTR_USER_ID, userId);
            return true;
        } catch (AuthException ex) {
            writeFail(response, ex.getCode(), ex.getMessage());
            return false;
        }
    }

    static boolean isWhitelisted(String path) {
        if (path == null) {
            return false;
        }
        return path.equals("/api/v1/auth/captcha")
                || path.equals("/api/v1/auth/login")
                || path.equals("/api/v1/auth/wechat/callback")
                || path.equals("/api/v1/auth/login/query")
                || path.equals("/api/v1/auth/refresh")
                || isOutsideApi(path);
    }

    static boolean isOutsideApi(String path) {
        return "/api/v1/outside".equals(path) || path.startsWith("/api/v1/outside/");
    }

    private static String extractBearer(String header) {
        if (header == null || header.isBlank()) {
            return null;
        }
        String trimmed = header.trim();
        if (trimmed.regionMatches(true, 0, "Bearer ", 0, 7)) {
            return trimmed.substring(7).trim();
        }
        return null;
    }

    private void writeFail(HttpServletResponse response, int code, String msg) throws Exception {
        response.setStatus(HttpServletResponse.SC_OK);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getWriter(), ApiResponse.fail(code, msg));
    }
}
