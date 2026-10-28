package com.zhibo.live.viewer;

import com.zhibo.live.watch.dto.WatchActivityListResponse;
import com.zhibo.live.web.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 小程序「我的学习」。需观众 access。
 * 直播列表只含 {@code activity_audience.entitlement_status=1} 的场次。
 */
@RestController
@RequestMapping("/api/v1/viewer/learning")
@RequiredArgsConstructor
public class ViewerLearningController {

    private final ViewerLearningService learningService;

    @GetMapping("/lives")
    public ApiResponse<WatchActivityListResponse> lives(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) Integer state,
            HttpServletRequest httpRequest) {
        return ApiResponse.ok(
                learningService.listEntitledLives(requireViewerId(httpRequest), page, pageSize, state));
    }

    private static long requireViewerId(HttpServletRequest request) {
        Long viewerId = (Long) request.getAttribute(ViewerAuthInterceptor.ATTR_VIEWER_ID);
        if (viewerId == null) {
            throw new ViewerAuthException(401, "未登录");
        }
        return viewerId;
    }
}
