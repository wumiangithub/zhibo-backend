package com.zhibo.live.viewer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhibo.live.web.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.nio.charset.StandardCharsets;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 观众接口 Bearer 鉴权。只挂 {@code /api/v1/viewer/**}。
 * 白名单是两个登录和一个刷新；观看和回调不进这里，也不查库。
 */
@Component
public class ViewerAuthInterceptor implements HandlerInterceptor {

    public static final String ATTR_VIEWER_ID = "viewerId";

    private final ViewerJwtTokenService jwtTokenService;
    private final ObjectMapper objectMapper;

    public ViewerAuthInterceptor(ViewerJwtTokenService jwtTokenService, ObjectMapper objectMapper) {
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
        if (!isViewerApi(path) || isWhitelisted(request)) {
            return true;
        }
        String token = extractBearer(request.getHeader("Authorization"));
        try {
            long viewerId = jwtTokenService.parseUserId(token);
            request.setAttribute(ATTR_VIEWER_ID, viewerId);
            return true;
        } catch (ViewerAuthException ex) {
            writeFail(response, ex.getCode(), ex.getMessage());
            return false;
        }
    }

    /** 观众业务路径。watch / callback 不是。 */
    static boolean isViewerApi(String path) {
        return path != null && (path.equals("/api/v1/viewer") || path.startsWith("/api/v1/viewer/"));
    }

    /** 只有三个 POST：微信一键、手机号、刷新。{@code /me} 不在里面。 */
    static boolean isWhitelisted(HttpServletRequest request) {
        if (request == null || !"POST".equalsIgnoreCase(request.getMethod())) {
            return false;
        }
        String path = request.getRequestURI();
        return "/api/v1/viewer/auth/wechat-login".equals(path)
                || "/api/v1/viewer/auth/phone-login".equals(path)
                || "/api/v1/viewer/auth/refresh".equals(path);
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
