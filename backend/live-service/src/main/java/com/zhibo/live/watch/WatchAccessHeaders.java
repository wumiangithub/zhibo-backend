package com.zhibo.live.watch;

import com.zhibo.live.viewer.ViewerJwtTokenService;
import jakarta.servlet.http.HttpServletRequest;

/** 从请求提取观众 id / 本场访问 JWT。 */
final class WatchAccessHeaders {

    private WatchAccessHeaders() {
    }

    static String activityAccess(HttpServletRequest request) {
        if (request == null) {
            return null;
        }
        String raw = request.getHeader(WatchAccessService.HEADER_ACTIVITY_ACCESS);
        return raw == null || raw.isBlank() ? null : raw.trim();
    }

    static Long tryViewerId(HttpServletRequest request, ViewerJwtTokenService jwt) {
        if (request == null || jwt == null) {
            return null;
        }
        String header = request.getHeader("Authorization");
        if (header == null || header.isBlank()) {
            return null;
        }
        String trimmed = header.trim();
        if (!trimmed.regionMatches(true, 0, "Bearer ", 0, 7)) {
            return null;
        }
        String token = trimmed.substring(7).trim();
        if (token.isEmpty()) {
            return null;
        }
        try {
            return jwt.parseUserId(token);
        } catch (RuntimeException ex) {
            return null;
        }
    }
}
