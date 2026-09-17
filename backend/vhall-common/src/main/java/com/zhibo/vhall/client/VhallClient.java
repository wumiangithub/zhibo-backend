package com.zhibo.vhall.client;

import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhibo.vhall.config.VhallProperties;
import com.zhibo.vhall.dto.VhallResponse;
import com.zhibo.vhall.exception.VhallException;
import com.zhibo.vhall.util.VhallSignUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.time.Instant;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 微吼开放平台 HTTP 客户端：补公共参数、签名、发 POST、解析 {@link VhallResponse}。
 * <p>
 * 类比前端封装过的 {@code request.post('/xxx', data)}：业务方只传 path + 业务字段，
 * 不要自己拼 {@code app_key}/{@code sign}。
 */
@Slf4j
public class VhallClient {

    private final VhallProperties properties;
    private final ObjectMapper objectMapper;
    private final RestClient restClient;

    public VhallClient(VhallProperties properties, ObjectMapper objectMapper) {
        this(properties, objectMapper, createRestClient(properties));
    }

    /** 测试可注入自定义 RestClient（配合 MockRestServiceServer）。 */
    VhallClient(VhallProperties properties, ObjectMapper objectMapper, RestClient restClient) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.restClient = restClient;
    }

    /**
     * POST 并返回完整微吼响应（即使业务 code != 200 也不抛，方便调用方自行判断）。
     *
     * @param path           如 {@code /v3/webinars/webinar/get-list}
     * @param businessParams 业务参数（不要放公共参数 / sign）
     * @param dataType       data 字段类型；无 data 可用 {@code Void.class}
     */
    public <T> VhallResponse<T> post(String path, Map<String, ?> businessParams, Class<T> dataType) {
        String requestId = newRequestId();
        Map<String, Object> allParams = buildSignedParams(businessParams);
        MultiValueMap<String, String> form = toForm(allParams);

        log.debug("Vhall POST {} request-id={}", path, requestId);
        String body;
        try {
            body = restClient.post()
                    .uri(path)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .header("platform", String.valueOf(properties.getPlatform()))
                    .header("request-id", requestId)
                    .body(form)
                    .retrieve()
                    .body(String.class);
        } catch (RestClientException e) {
            throw new VhallException("微吼 HTTP 调用失败: " + path, e);
        }

        return parseResponse(body, dataType);
    }

    /**
     * POST 且要求业务成功：返回 data，失败抛 {@link VhallException}。
     */
    public <T> T postForData(String path, Map<String, ?> businessParams, Class<T> dataType) {
        return post(path, businessParams, dataType).requireData();
    }

    private Map<String, Object> buildSignedParams(Map<String, ?> businessParams) {
        Map<String, Object> all = new LinkedHashMap<>();
        if (businessParams != null) {
            businessParams.forEach((k, v) -> {
                if (k != null && v != null && !"sign".equals(k)) {
                    all.put(k, v);
                }
            });
        }
        all.put("app_key", properties.getAppKey());
        all.put("sign_type", properties.getSignType());
        all.put("signed_at", Instant.now().getEpochSecond());
        all.put("sign", VhallSignUtil.sign(properties.getAppSecret(), all));
        return all;
    }

    private MultiValueMap<String, String> toForm(Map<String, Object> params) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        params.forEach((key, value) -> {
            if (value instanceof Collection<?> collection) {
                for (Object item : collection) {
                    if (item != null) {
                        form.add(key, String.valueOf(item));
                    }
                }
            } else if (value instanceof Object[] array) {
                for (Object item : array) {
                    if (item != null) {
                        form.add(key, String.valueOf(item));
                    }
                }
            } else if (value != null) {
                form.add(key, String.valueOf(value).trim());
            }
        });
        return form;
    }

    private <T> VhallResponse<T> parseResponse(String body, Class<T> dataType) {
        if (body == null || body.isBlank()) {
            throw new VhallException("微吼响应体为空");
        }
        try {
            JavaType type = objectMapper.getTypeFactory()
                    .constructParametricType(VhallResponse.class, dataType);
            return objectMapper.readValue(body, type);
        } catch (Exception e) {
            throw new VhallException("微吼响应解析失败: " + abbreviate(body), e);
        }
    }

    private static String newRequestId() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    private static String abbreviate(String body) {
        return body.length() <= 200 ? body : body.substring(0, 200) + "...";
    }

    private static RestClient createRestClient(VhallProperties properties) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(properties.getConnectTimeout());
        factory.setReadTimeout(properties.getReadTimeout());
        String baseUrl = trimTrailingSlash(properties.getBaseUrl());
        return RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(factory)
                .build();
    }

    private static String trimTrailingSlash(String baseUrl) {
        if (baseUrl == null || baseUrl.isBlank()) {
            return "https://saas-open.vhall.com";
        }
        return baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
    }
}
