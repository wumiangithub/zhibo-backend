package com.zhibo.vhall.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhibo.vhall.config.VhallProperties;
import com.zhibo.vhall.dto.VhallResponse;
import com.zhibo.vhall.exception.VhallException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
}
