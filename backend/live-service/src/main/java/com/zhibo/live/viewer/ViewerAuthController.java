package com.zhibo.live.viewer;

import com.zhibo.live.viewer.dto.DevPhoneLoginRequest;
import com.zhibo.live.viewer.dto.PhoneLoginRequest;
import com.zhibo.live.viewer.dto.ViewerAuthResponse;
import com.zhibo.live.viewer.dto.ViewerRefreshRequest;
import com.zhibo.live.viewer.dto.ViewerUserResponse;
import com.zhibo.live.viewer.dto.WechatLoginRequest;
import com.zhibo.live.web.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
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
    private final Environment environment;

    public ViewerAuthController(ViewerAuthService viewerAuthService, Environment environment) {
        this.viewerAuthService = viewerAuthService;
        this.environment = environment;
    }

    @PostMapping("/wechat-login")
    public ApiResponse<ViewerAuthResponse> wechatLogin(@Valid @RequestBody WechatLoginRequest request) {
        return ApiResponse.ok(viewerAuthService.wechatLogin(request));
    }

    @PostMapping("/phone-login")
    public ApiResponse<ViewerAuthResponse> phoneLogin(@Valid @RequestBody PhoneLoginRequest request) {
        return ApiResponse.ok(viewerAuthService.phoneLogin(request));
    }

    /**
     * 仅 spring profile=local：明文手机号登录 / 自动注册，给开发造号。
     * test / prod 一律业务 404，不暴露能力。
     */
    @PostMapping("/dev-phone-login")
    public ApiResponse<ViewerAuthResponse> devPhoneLogin(@Valid @RequestBody DevPhoneLoginRequest request) {
        if (!environment.acceptsProfiles(Profiles.of("local"))) {
            throw new ViewerAuthException(404, "接口不存在");
        }
        return ApiResponse.ok(viewerAuthService.devPhoneLogin(request));
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
