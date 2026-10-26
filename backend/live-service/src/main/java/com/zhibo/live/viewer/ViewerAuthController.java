package com.zhibo.live.viewer;

import com.zhibo.live.viewer.dto.PhoneLoginRequest;
import com.zhibo.live.viewer.dto.ViewerAuthResponse;
import com.zhibo.live.viewer.dto.ViewerRefreshRequest;
import com.zhibo.live.viewer.dto.ViewerUserResponse;
import com.zhibo.live.viewer.dto.WechatLoginRequest;
import com.zhibo.live.web.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 观众登录。都在 live-service，不进管理端。 */
@RestController
@RequestMapping("/api/v1/viewer/auth")
public class ViewerAuthController {

    private final ViewerAuthService viewerAuthService;

    public ViewerAuthController(ViewerAuthService viewerAuthService) {
        this.viewerAuthService = viewerAuthService;
    }

    @PostMapping("/wechat-login")
    public ApiResponse<ViewerAuthResponse> wechatLogin(@Valid @RequestBody WechatLoginRequest request) {
        return ApiResponse.ok(viewerAuthService.wechatLogin(request));
    }

    @PostMapping("/phone-login")
    public ApiResponse<ViewerAuthResponse> phoneLogin(@Valid @RequestBody PhoneLoginRequest request) {
        return ApiResponse.ok(viewerAuthService.phoneLogin(request));
    }

    @PostMapping("/refresh")
    public ApiResponse<ViewerAuthResponse> refresh(@Valid @RequestBody ViewerRefreshRequest request) {
        return ApiResponse.ok(viewerAuthService.refresh(request));
    }

    @GetMapping("/user-info")
    public ApiResponse<ViewerUserResponse> userInfo(HttpServletRequest request) {
        Long viewerId = (Long) request.getAttribute(ViewerAuthInterceptor.ATTR_VIEWER_ID);
        if (viewerId == null) {
            throw new ViewerAuthException(401, "未登录");
        }
        return ApiResponse.ok(viewerAuthService.userInfo(viewerId));
    }
}
