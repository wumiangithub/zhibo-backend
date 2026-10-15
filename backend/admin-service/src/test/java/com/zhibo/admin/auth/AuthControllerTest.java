package com.zhibo.admin.auth;

import com.zhibo.admin.auth.dto.AuthUserResponse;
import com.zhibo.admin.auth.dto.CaptchaResponse;
import com.zhibo.admin.auth.dto.LoginPendingResponse;
import com.zhibo.admin.auth.dto.LoginQueryResponse;
import com.zhibo.admin.web.GlobalExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AuthControllerTest {

    private MockMvc mockMvc;
    private AuthService authService;
    private Environment environment;

    @BeforeEach
    void setUp() {
        authService = mock(AuthService.class);
        environment = mock(Environment.class);
        when(environment.acceptsProfiles(any(Profiles.class))).thenReturn(true);
        mockMvc = MockMvcBuilders.standaloneSetup(new AuthController(authService, environment))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void captcha_ok() throws Exception {
        when(authService.captcha()).thenReturn(new CaptchaResponse("cid", "data:image/png;base64,xx"));

        mockMvc.perform(get("/api/auth/captcha"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.captchaId").value("cid"));
    }

    @Test
    void login_ok() throws Exception {
        when(authService.login(any())).thenReturn(new LoginPendingResponse("NCRM_abc", 180));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "userAccount":"admin",
                                  "password":"cipher",
                                  "captchaId":"cid",
                                  "captchaCode":"ab12"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.state").value("NCRM_abc"))
                .andExpect(jsonPath("$.data.expiresIn").value(180));
    }

    @Test
    void login_disabled_returns403() throws Exception {
        when(authService.login(any())).thenThrow(new AuthException(403, "账号已停用"));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "userAccount":"admin",
                                  "password":"cipher",
                                  "captchaId":"cid",
                                  "captchaCode":"ab12"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(403))
                .andExpect(jsonPath("$.msg").value("账号已停用"));
    }

    @Test
    void query_pending() throws Exception {
        LoginQueryResponse resp = new LoginQueryResponse();
        resp.setStatus(0);
        when(authService.query("NCRM_x")).thenReturn(resp);

        mockMvc.perform(get("/api/auth/login/query").param("state", "NCRM_x"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.status").value(0));
    }

    @Test
    void wechatCallback_local_returnsHtmlRedirect() throws Exception {
        when(environment.acceptsProfiles(any(Profiles.class))).thenReturn(true);

        mockMvc.perform(get("/api/auth/wechat/callback")
                        .param("code", "c")
                        .param("state", "NCRM_x"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML))
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "location.replace('/login?wx_done=1')")));
    }

    @Test
    void wechatCallback_nonLocal_returnsOkPageWithoutReplace() throws Exception {
        when(environment.acceptsProfiles(any(Profiles.class))).thenReturn(false);

        mockMvc.perform(get("/api/auth/wechat/callback")
                        .param("code", "c")
                        .param("state", "NCRM_x"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML))
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "OK. You can close this page.")))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString(
                        "location.replace"))));
    }

    @Test
    void wechatCallback_failureStillUsesEnvPage() throws Exception {
        when(environment.acceptsProfiles(any(Profiles.class))).thenReturn(true);
        org.mockito.Mockito.doThrow(new IllegalArgumentException("登录会话无效或已过期"))
                .when(authService).handleWechatCallback("c", "bad");

        mockMvc.perform(get("/api/auth/wechat/callback")
                        .param("code", "c")
                        .param("state", "bad"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "location.replace('/login?wx_done=1')")));
    }

    @Test
    void wechatCallback_failure_nonLocal_noReplace() throws Exception {
        when(environment.acceptsProfiles(any(Profiles.class))).thenReturn(false);
        org.mockito.Mockito.doThrow(new IllegalArgumentException("登录会话无效或已过期"))
                .when(authService).handleWechatCallback("c", "bad");

        mockMvc.perform(get("/api/auth/wechat/callback")
                        .param("code", "c")
                        .param("state", "bad"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "OK. You can close this page.")))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString(
                        "location.replace"))));
    }

    @Test
    void me_withoutUserAttr_returns401() throws Exception {
        mockMvc.perform(get("/api/auth/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(401));
    }

    @Test
    void me_ok() throws Exception {
        when(authService.me(eq(1L)))
                .thenReturn(new AuthUserResponse(1L, "admin", "张三", null, null));

        mockMvc.perform(get("/api/auth/me").requestAttr(AuthInterceptor.ATTR_USER_ID, 1L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.userAccount").value("admin"));
    }
}
