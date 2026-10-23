package com.zhibo.live.viewer;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.zhibo.live.viewer.dto.ViewerAuthResponse;
import com.zhibo.live.viewer.dto.ViewerUserResponse;
import com.zhibo.live.web.GlobalExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

class ViewerAuthControllerTest {

    private MockMvc mockMvc;
    private ViewerAuthService viewerAuthService;

    @BeforeEach
    void setUp() {
        viewerAuthService = mock(ViewerAuthService.class);
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        mockMvc = MockMvcBuilders.standaloneSetup(new ViewerAuthController(viewerAuthService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setValidator(validator)
                .build();
    }

    @Test
    void wechatLogin_notAgreed_http200Business400() throws Exception {
        mockMvc.perform(post("/api/v1/viewer/auth/wechat-login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"abc\",\"agreed\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.msg").value("请先阅读并同意用户协议和隐私协议"));
        verifyNoInteractions(viewerAuthService);
    }

    @Test
    void wechatLogin_withoutPhone_omitsPhoneMasked() throws Exception {
        ViewerUserResponse user = new ViewerUserResponse();
        user.setId(1L);
        user.setHasPhone(false);
        ViewerAuthResponse response = new ViewerAuthResponse();
        response.setAccessToken("access");
        response.setExpiresIn(7200);
        response.setRefreshToken("refresh");
        response.setRefreshExpiresIn(604800);
        response.setUser(user);
        when(viewerAuthService.wechatLogin(any())).thenReturn(response);

        mockMvc.perform(post("/api/v1/viewer/auth/wechat-login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"abc\",\"agreed\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.user.id").value(1))
                .andExpect(jsonPath("$.data.user.hasPhone").value(false))
                .andExpect(jsonPath("$.data.user.phoneMasked").doesNotExist())
                .andExpect(jsonPath("$.data.expiresIn").value(7200));
    }
}
