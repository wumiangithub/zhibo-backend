package com.zhibo.live.viewer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ViewerOauthClientTest {

    private ViewerAuthProperties properties;
    private ViewerOauthClient client;
    private String lastUrl;
    private String lastBody;
    private String responseBody;

    @BeforeEach
    void setUp() {
        properties = new ViewerAuthProperties();
        properties.getOauth().setBaseUrl("http://119.3.31.54:8410/acctClientManagement/");
        lastUrl = null;
        lastBody = null;
        responseBody = "{\"code\":0,\"data\":{\"uid\":90001,\"username\":\"qk_u_1\",\"status\":0}}";
        client = new ViewerOauthClient(properties, new ObjectMapper(), (url, body) -> {
            lastUrl = url;
            lastBody = body;
            return responseBody;
        });
    }

    @Test
    void registerByUnionId_postsRequiredFieldsWithoutNickname() {
        ViewerOauthClient.OauthIdentity identity = client.registerByUnionId("union-abc", "phone");
        assertEquals(90001L, identity.uid());
        assertEquals("qk_u_1", identity.username());
        assertEquals("http://119.3.31.54:8410/acctClientManagement/oauth/wechat/register", lastUrl);
        assertEquals(
                "{\"unionId\":\"union-abc\",\"channel\":\"钱坤云私域直播用户注册\",\"projectName\":\"钱坤云私域直播\",\"projectCode\":\"qkysyzb\",\"type\":\"phone\"}",
                lastBody);
    }

    @Test
    void registerByUnionId_rejectsFrozenAccount() {
        responseBody = "{\"code\":0,\"data\":{\"uid\":1,\"username\":\"x\",\"status\":1}}";
        ViewerAuthException ex =
                assertThrows(ViewerAuthException.class, () -> client.registerByUnionId("u1", "pc"));
        assertEquals(400, ex.getCode());
        assertEquals("登录失败，请重试", ex.getMessage());
    }

    @Test
    void registerByUnionId_requiresBaseUrl() {
        properties.getOauth().setBaseUrl(" ");
        ViewerAuthException ex =
                assertThrows(ViewerAuthException.class, () -> client.registerByUnionId("u1", "phone"));
        assertEquals(500, ex.getCode());
        assertEquals("观众登录暂不可用", ex.getMessage());
    }
}
