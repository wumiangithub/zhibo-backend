package com.zhibo.admin.auth;

import com.zhibo.admin.auth.dto.AuthUserResponse;
import com.zhibo.admin.auth.dto.CaptchaResponse;
import com.zhibo.admin.auth.dto.LoginPendingResponse;
import com.zhibo.admin.auth.dto.LoginQueryResponse;
import com.zhibo.admin.auth.dto.LoginRequest;
import com.zhibo.admin.web.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    /** local：顶层跳回登录页，由前端用 state 轮询 query（避免 iframe + Chrome PNA）。 */
    static final String CALLBACK_HTML_LOCAL = """
            <!DOCTYPE html>
            <html><head><meta charset="utf-8">
            <script>location.replace('/login?wx_done=1')</script>
            </head><body></body></html>
            """;

    /** 生产/测试等：极简 OK 页，不自动跳转。 */
    static final String CALLBACK_HTML_REMOTE = """
            <!DOCTYPE html>
            <html><head><meta charset="utf-8"></head>
            <body>OK. You can close this page.</body></html>
            """;

    private final AuthService authService;
    private final Environment environment;

    public AuthController(AuthService authService, Environment environment) {
        this.authService = authService;
        this.environment = environment;
    }

    @GetMapping("/captcha")
    public ApiResponse<CaptchaResponse> captcha() {
        return ApiResponse.ok(authService.captcha());
    }

    @PostMapping("/login")
    public ApiResponse<LoginPendingResponse> login(@Valid @RequestBody LoginRequest request) {
        return ApiResponse.ok(authService.login(request));
    }

    @GetMapping(value = "/wechat/callback", produces = MediaType.TEXT_HTML_VALUE)
    public String wechatCallbackGet(
            @RequestParam(required = false) String code,
            @RequestParam(required = false) String state) {
        return handleCallback(code, state);
    }

    @PostMapping(value = "/wechat/callback", produces = MediaType.TEXT_HTML_VALUE)
    public String wechatCallbackPost(
            @RequestParam(required = false) String code,
            @RequestParam(required = false) String state) {
        return handleCallback(code, state);
    }

    @GetMapping("/login/query")
    public ApiResponse<LoginQueryResponse> query(@RequestParam String state) {
        return ApiResponse.ok(authService.query(state));
    }

    @GetMapping("/me")
    public ApiResponse<AuthUserResponse> me(HttpServletRequest request) {
        Long userId = (Long) request.getAttribute(AuthInterceptor.ATTR_USER_ID);
        if (userId == null) {
            throw new AuthException(401, "未登录");
        }
        return ApiResponse.ok(authService.me(userId));
    }

    private String handleCallback(String code, String state) {
        try {
            authService.handleWechatCallback(code, state);
        } catch (Exception ex) {
            // 成功/失败同一套按环境选页；错误态由 query status=2 + errorMsg 传达
            return callbackHtml();
        }
        return callbackHtml();
    }

    String callbackHtml() {
        if (environment.acceptsProfiles(Profiles.of("local"))) {
            return CALLBACK_HTML_LOCAL;
        }
        return CALLBACK_HTML_REMOTE;
    }
}
