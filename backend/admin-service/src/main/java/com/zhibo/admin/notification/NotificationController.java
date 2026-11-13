package com.zhibo.admin.notification;

import com.zhibo.admin.auth.AuthException;
import com.zhibo.admin.auth.AuthInterceptor;
import com.zhibo.admin.notification.dto.NotificationIdResponse;
import com.zhibo.admin.notification.dto.NotificationListResponse;
import com.zhibo.admin.notification.dto.NotificationReadAllResponse;
import com.zhibo.admin.notification.dto.NotificationUnreadCountResponse;
import com.zhibo.admin.web.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 工作台通知中心。须管理端登录（路径未进白名单）。
 */
@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final AdminNotificationService adminNotificationService;

    @GetMapping("/list")
    public ApiResponse<NotificationListResponse> list(
            @RequestParam(value = "page", required = false) Integer page,
            @RequestParam(value = "pageSize", required = false) Integer pageSize,
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "type", required = false) Integer type,
            @RequestParam(value = "auditResult", required = false) Integer auditResult,
            HttpServletRequest httpRequest) {
        return ApiResponse.ok(
                adminNotificationService.list(
                        currentUserId(httpRequest), page, pageSize, keyword, type, auditResult));
    }

    @GetMapping("/unread-count")
    public ApiResponse<NotificationUnreadCountResponse> unreadCount(HttpServletRequest httpRequest) {
        return ApiResponse.ok(adminNotificationService.unreadCount(currentUserId(httpRequest)));
    }

    @PostMapping("/read/{id}")
    public ApiResponse<NotificationIdResponse> readOne(
            @PathVariable String id, HttpServletRequest httpRequest) {
        return ApiResponse.ok(adminNotificationService.readOne(currentUserId(httpRequest), id));
    }

    @PostMapping("/read-all")
    public ApiResponse<NotificationReadAllResponse> readAll(HttpServletRequest httpRequest) {
        return ApiResponse.ok(adminNotificationService.readAll(currentUserId(httpRequest)));
    }

    private static long currentUserId(HttpServletRequest request) {
        Object raw = request.getAttribute(AuthInterceptor.ATTR_USER_ID);
        if (raw instanceof Number number) {
            return number.longValue();
        }
        throw new AuthException(401, "未登录");
    }
}
