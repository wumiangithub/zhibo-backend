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
 * 观众接口 Bearer 鉴权。挂 {@code /api/v1/viewer/**}、回放上报 {@code /api/v1/watch/replay/**}、
 * 邀请归因 {@code /api/v1/watch/audience/**}、评论上报 {@code /api/v1/watch/comments/**}。
 */
@Component
public class ViewerAuthInterceptor implements HandlerInterceptor {

    /** 请求属性：用户中心 username（JWT sub，D118）。 */
    public static final String ATTR_VIEWER_USERNAME = "viewerUsername";

    /** 从拦截器写入的请求属性读取观众 username；缺失或空白 → 401。 */
    public static String requireViewerUsername(HttpServletRequest request) {
        if (request == null) {
            throw new ViewerAuthException(401, "未登录");
        }
        String username = (String) request.getAttribute(ATTR_VIEWER_USERNAME);
        if (username == null || username.isBlank()) {
            throw new ViewerAuthException(401, "未登录");
        }
        return username.trim();
    }

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
            String username = jwtTokenService.parseUsername(token);
            request.setAttribute(ATTR_VIEWER_USERNAME, username);
            return true;
        } catch (ViewerAuthException ex) {
            writeFail(response, ex.getCode(), ex.getMessage());
            return false;
        }
    }

    static boolean isViewerApi(String path) {
        return path != null && (path.equals("/api/v1/viewer")
                || path.startsWith("/api/v1/viewer/")
                || path.startsWith("/api/v1/watch/replay/")
                || path.startsWith("/api/v1/watch/audience/")
                || path.startsWith("/api/v1/watch/course/")
                || path.startsWith("/api/v1/watch/comments/"));
    }

    static boolean isWhitelisted(HttpServletRequest request) {
        if (request == null || !"POST".equalsIgnoreCase(request.getMethod())) {
            return false;
        }
        String path = request.getRequestURI();
        return "/api/v1/viewer/auth/wechat-login".equals(path)
                || "/api/v1/viewer/auth/phone-login".equals(path)
                || "/api/v1/viewer/auth/dev-phone-login".equals(path)
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
