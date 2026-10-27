package com.zhibo.live.viewer;

import com.zhibo.live.viewer.dto.ViewerPresencePingRequest;
import com.zhibo.live.viewer.dto.ViewerPresencePingResponse;
import com.zhibo.live.web.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 观众访问心跳（D79）。需 access。 */
@RestController
@RequestMapping("/api/v1/viewer/presence")
public class ViewerPresenceController {

    private final ViewerPresenceService presenceService;

    public ViewerPresenceController(ViewerPresenceService presenceService) {
        this.presenceService = presenceService;
    }

    @PostMapping("/ping")
    public ApiResponse<ViewerPresencePingResponse> ping(
            @RequestBody(required = false) ViewerPresencePingRequest request, HttpServletRequest httpRequest) {
        String scene = request == null ? null : request.getScene();
        return ApiResponse.ok(presenceService.ping(requireViewerId(httpRequest), scene));
    }

    private static long requireViewerId(HttpServletRequest request) {
        Long viewerId = (Long) request.getAttribute(ViewerAuthInterceptor.ATTR_VIEWER_ID);
        if (viewerId == null) {
            throw new ViewerAuthException(401, "未登录");
        }
        return viewerId;
    }
}
