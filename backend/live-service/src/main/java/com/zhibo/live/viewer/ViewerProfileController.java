package com.zhibo.live.viewer;

import com.zhibo.live.viewer.dto.ViewerProfileUpdateRequest;
import com.zhibo.live.viewer.dto.ViewerUserResponse;
import com.zhibo.live.web.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 观众昵称 / 头像。需 access。 */
@RestController
@RequestMapping("/api/v1/viewer/profile")
public class ViewerProfileController {

    private final ViewerProfileService viewerProfileService;

    public ViewerProfileController(ViewerProfileService viewerProfileService) {
        this.viewerProfileService = viewerProfileService;
    }

    @PutMapping("/update")
    public ApiResponse<ViewerUserResponse> update(
            @RequestBody ViewerProfileUpdateRequest request, HttpServletRequest httpRequest) {
        return ApiResponse.ok(viewerProfileService.update(requireViewerId(httpRequest), request));
    }

    private static long requireViewerId(HttpServletRequest request) {
        Long viewerId = (Long) request.getAttribute(ViewerAuthInterceptor.ATTR_VIEWER_ID);
        if (viewerId == null) {
            throw new ViewerAuthException(401, "未登录");
        }
        return viewerId;
    }
}
