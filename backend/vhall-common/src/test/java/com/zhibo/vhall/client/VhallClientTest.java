package com.zhibo.vhall.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhibo.vhall.config.VhallProperties;
import com.zhibo.vhall.dto.VhallResponse;
import com.zhibo.vhall.exception.VhallException;
import com.zhibo.vhall.util.VhallSignUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class VhallClientTest {

    private MockRestServiceServer server;
    private VhallClient client;

    @BeforeEach
    void setUp() {
        VhallProperties properties = new VhallProperties();
        properties.setBaseUrl("https://saas-open.vhall.com");
        properties.setAppKey("test_app_key");
        properties.setAppSecret("test_secret");
        properties.setSignType(0);
        properties.setPlatform(15);

        RestClient.Builder builder = RestClient.builder().baseUrl(properties.getBaseUrl());
        server = MockRestServiceServer.bindTo(builder).build();
        client = new VhallClient(properties, new ObjectMapper(), builder.build());
    }

    @Test
    void post_sendsFormAndParsesSuccess() {
        server.expect(requestTo("https://saas-open.vhall.com/v3/webinars/webinar/get-list"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("platform", "15"))
                .andExpect(header("request-id", org.hamcrest.Matchers.not(org.hamcrest.Matchers.emptyOrNullString())))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_FORM_URLENCODED))
                .andExpect(content().formDataContains(Map.of(
                        "app_key", "test_app_key",
                        "sign_type", "0",
                        "pos", "0",
                        "limit", "10"
                )))
                .andRespond(withSuccess("""
                        {"code":200,"msg":"success","data":{"total":1},"request_id":"rid-1"}
                        """, MediaType.APPLICATION_JSON));

        VhallResponse<Map> response = client.post(
                "/v3/webinars/webinar/get-list",
                Map.of("pos", 0, "limit", 10),
                Map.class);

        assertTrue(response.isSuccess());
        assertEquals("rid-1", response.getRequestId());
        assertEquals(1, ((Number) response.requireData().get("total")).intValue());
        server.verify();
    }

    @Test
    void postForData_throwsWhenBusinessCodeNotSuccess() {
        server.expect(requestTo("https://saas-open.vhall.com/v3/webinars/webinar/create"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("""
                        {"code":600,"msg":"参数错误","data":{},"request_id":"rid-err"}
                        """, MediaType.APPLICATION_JSON));

        VhallException ex = assertThrows(VhallException.class,
                () -> client.postForData("/v3/webinars/webinar/create",
                        Map.of("subject", "demo"), Void.class));

        assertEquals(600, ex.getCode());
        assertEquals("rid-err", ex.getRequestId());
        assertEquals("参数错误", ex.getMessage());
        server.verify();
    }

    @Test
    void post_wrapsHttpFailureAsVhallException() {
        server.expect(requestTo("https://saas-open.vhall.com/v3/webinars/webinar/get-list"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withServerError());

        VhallException ex = assertThrows(VhallException.class,
                () -> client.post("/v3/webinars/webinar/get-list", Map.of("pos", 0), Map.class));

        assertTrue(ex.getMessage().contains("HTTP"));
        assertTrue(ex.getCause() != null);
        server.verify();
    }

    @Test
    void get_sendsSignedQueryAndParses() {
        server.expect(requestTo(org.hamcrest.Matchers.startsWith(
                        "https://saas-open.vhall.com/v3/webinars/live/get-role-url?")))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("platform", "15"))
                .andRespond(withSuccess("""
                        {"code":200,"msg":"ok","data":{"page_url":"https://host"},"request_id":"g1"}
                        """, MediaType.APPLICATION_JSON));

        Map data = client.getForData("/v3/webinars/live/get-role-url",
                Map.of("webinar_id", 1, "type", 1), Map.class);
        assertEquals("https://host", data.get("page_url"));
        server.verify();
    }

    /**
     * avatar 常含 {@code ?}&amp;；未 encode 会把 query 截断；签名须用原始值，且不得双重编码。
     */
    @Test
    void get_encodesAvatarQueryWithoutDoubleEncodingSign() {
        String avatar = "https://cdn.example/a.png?x=1&y=2";
        String nickname = "主播 A";

        server.expect(request -> {
                    assertEquals(HttpMethod.GET, request.getMethod());
                    String rawQuery = request.getURI().getRawQuery();
                    // 截断特征：裸 &y=2；双重编码特征：%253D / %2526 / %25E4
                    assertFalse(rawQuery.contains("&y=2"), () -> "avatar 被截断: " + rawQuery);
                    assertFalse(rawQuery.contains("%25"), () -> "疑似双重编码: " + rawQuery);
                    assertTrue(rawQuery.contains("%26") || rawQuery.contains("%3D"),
                            () -> "avatar 特殊字符未 encode: " + rawQuery);

                    Map<String, String> decoded = decodeQuery(rawQuery);
                    assertEquals(avatar, decoded.get("avatar"));
                    assertEquals(nickname, decoded.get("nickname"));
                    assertEquals("1", decoded.get("webinar_id"));
                    assertEquals("1", decoded.get("type"));
                    assertFalse(decoded.containsKey("y"), () -> "avatar 的 y 不应成为独立 query: " + decoded);

                    Map<String, Object> forSign = new LinkedHashMap<>(decoded);
                    String actualSign = (String) forSign.remove("sign");
                    assertEquals(
                            VhallSignUtil.sign("test_secret", forSign),
                            actualSign,
                            "签名须对原始（解码后）参数值计算，禁止对已 encode 的串签名");
                })
                .andRespond(withSuccess("""
                        {"code":200,"msg":"ok","data":{"page_url":"https://host"},"request_id":"g2"}
                        """, MediaType.APPLICATION_JSON));

        Map data = client.getForData(
                "/v3/webinars/live/get-role-url",
                Map.of(
                        "webinar_id", 1,
                        "type", 1,
                        "nickname", nickname,
                        "avatar", avatar),
                Map.class);
        assertEquals("https://host", data.get("page_url"));
        server.verify();
    }

    private static Map<String, String> decodeQuery(String rawQuery) {
        Map<String, String> out = new LinkedHashMap<>();
        for (String pair : rawQuery.split("&")) {
            int eq = pair.indexOf('=');
            String key = URLDecoder.decode(eq < 0 ? pair : pair.substring(0, eq), StandardCharsets.UTF_8);
            String value = eq < 0 ? "" : URLDecoder.decode(pair.substring(eq + 1), StandardCharsets.UTF_8);
            out.put(key, value);
        }
        return out;
    }

    @Test
    void postMultipart_sendsMultipartAndParsesDomainUrl() {
        server.expect(requestTo("https://saas-open.vhall.com/v3/commons/upload/index"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("platform", "15"))
                .andExpect(content().contentTypeCompatibleWith(MediaType.MULTIPART_FORM_DATA))
                .andRespond(withSuccess("""
                        {"code":200,"msg":"","data":{"file_url":"a/b.jpg","domain_url":"https://cdn.example/a/b.jpg","lines":"0"},"request_id":"up1"}
                        """, MediaType.APPLICATION_JSON));

        org.springframework.core.io.ByteArrayResource file =
                new org.springframework.core.io.ByteArrayResource("fake-image".getBytes()) {
                    @Override
                    public String getFilename() {
                        return "cover.jpg";
                    }
                };

        Map data = client.postMultipartForData(
                "/v3/commons/upload/index",
                Map.of("type", "image"),
                "resfile",
                file,
                Map.class);

        assertEquals("https://cdn.example/a/b.jpg", data.get("domain_url"));
        server.verify();
    }
}
