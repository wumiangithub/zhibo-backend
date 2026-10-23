package com.zhibo.live.viewer;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * 微信小程序登录：code 换 openid，以及手机号组件。
 * 不读取、不记录 session_key。access_token 只放内存，提前约 2 分钟刷新。
 */
@Slf4j
@Component
public class WechatMpClient {

    private static final String CODE2SESSION = "https://api.weixin.qq.com/sns/jscode2session";
    private static final String CLIENT_TOKEN = "https://api.weixin.qq.com/cgi-bin/token";
    private static final String PHONE_NUMBER = "https://api.weixin.qq.com/wxa/business/getuserphonenumber";

    private final ViewerAuthProperties properties;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final WechatHttp http;
    private final Object tokenLock = new Object();
    private volatile CachedToken cachedToken;

    @Autowired
    public WechatMpClient(ViewerAuthProperties properties, ObjectMapper objectMapper, Clock clock) {
        this(properties, objectMapper, clock, new RestWechatHttp());
    }

    WechatMpClient(ViewerAuthProperties properties, ObjectMapper objectMapper, Clock clock, WechatHttp http) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.clock = clock;
        this.http = http;
    }

    /** jscode2session 的结果。没有 session_key。 */
    public record WechatSession(String openid, String unionid) {
    }

    /** getuserphonenumber 的纯手机号和国家码。 */
    public record WechatPhone(String purePhoneNumber, String countryCode) {
    }

    public WechatSession code2Session(String code) {
        requireApp();
        String url = UriComponentsBuilder.fromUriString(CODE2SESSION)
                .queryParam("appid", appId())
                .queryParam("secret", appSecret())
                .queryParam("js_code", code)
                .queryParam("grant_type", "authorization_code")
                .build()
                .encode()
                .toUriString();
        JsonNode node = read(http.exchange("GET", url, null));
        ensureOk(node);
        String openid = text(node, "openid");
        if (openid == null || openid.length() > 64) {
            log.warn("微信接口失败 errcode={}", node.path("errcode").asInt(-1));
            throw new ViewerAuthException(400, "微信授权失败，请重试");
        }
        String unionid = text(node, "unionid");
        if (unionid != null && unionid.length() > 64) {
            unionid = null;
        }
        return new WechatSession(openid, unionid);
    }

    public WechatPhone getPhoneNumber(String phoneCode) {
        requireApp();
        String token = clientCredentialToken();
        String url = UriComponentsBuilder.fromUriString(PHONE_NUMBER)
                .queryParam("access_token", token)
                .build()
                .encode()
                .toUriString();
        String body;
        try {
            body = objectMapper.writeValueAsString(Map.of("code", phoneCode));
        } catch (Exception ex) {
            throw new ViewerAuthException(400, "微信授权失败，请重试");
        }
        JsonNode node = read(http.exchange("POST", url, body));
        ensureOk(node);
        JsonNode info = node.path("phone_info");
        String phone = text(info, "purePhoneNumber");
        if (phone == null) {
            log.warn("微信接口失败 errcode={}", node.path("errcode").asInt(-1));
            throw new ViewerAuthException(400, "微信授权失败，请重试");
        }
        return new WechatPhone(phone, text(info, "countryCode"));
    }

    private String clientCredentialToken() {
        CachedToken current = cachedToken;
        Instant now = clock.instant();
        if (current != null && now.isBefore(current.expireAt())) {
            return current.value();
        }
        synchronized (tokenLock) {
            current = cachedToken;
            now = clock.instant();
            if (current != null && now.isBefore(current.expireAt())) {
                return current.value();
            }
            String url = UriComponentsBuilder.fromUriString(CLIENT_TOKEN)
                    .queryParam("grant_type", "client_credential")
                    .queryParam("appid", appId())
                    .queryParam("secret", appSecret())
                    .build()
                    .encode()
                    .toUriString();
            JsonNode node = read(http.exchange("GET", url, null));
            ensureOk(node);
            String token = text(node, "access_token");
            if (token == null) {
                log.warn("微信接口失败 errcode={}", node.path("errcode").asInt(-1));
                throw new ViewerAuthException(400, "微信授权失败，请重试");
            }
            int expiresIn = node.path("expires_in").asInt(7200);
            long ttl = Math.max(30, expiresIn - 120L);
            cachedToken = new CachedToken(token, now.plusSeconds(ttl));
            return token;
        }
    }

    private void requireApp() {
        if (!properties.wechatCredentialsMissing()) {
            return;
        }
        if (appId().isBlank()) {
            log.warn("观众登录配置缺失: WECHAT_MP_APP_ID");
        }
        if (appSecret().isBlank()) {
            log.warn("观众登录配置缺失: WECHAT_MP_APP_SECRET");
        }
        throw new ViewerAuthException(500, "观众登录暂不可用");
    }

    private void ensureOk(JsonNode node) {
        if (node.hasNonNull("errcode") && node.get("errcode").asInt() != 0) {
            log.warn("微信接口失败 errcode={}", node.get("errcode").asInt());
            throw new ViewerAuthException(400, "微信授权失败，请重试");
        }
    }

    private JsonNode read(String body) {
        try {
            return objectMapper.readTree(body == null || body.isBlank() ? "{}" : body);
        } catch (Exception ex) {
            log.warn("微信响应无法解析");
            throw new ViewerAuthException(400, "微信授权失败，请重试");
        }
    }

    private String appId() {
        if (properties.getWechat() == null || properties.getWechat().getMpAppId() == null) {
            return "";
        }
        return properties.getWechat().getMpAppId().trim();
    }

    private String appSecret() {
        if (properties.getWechat() == null || properties.getWechat().getMpAppSecret() == null) {
            return "";
        }
        return properties.getWechat().getMpAppSecret().trim();
    }

    private static String text(JsonNode node, String field) {
        if (node == null || node.isMissingNode()) {
            return null;
        }
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) {
            return null;
        }
        String text = value.asText();
        return text == null || text.isBlank() ? null : text;
    }

    private record CachedToken(String value, Instant expireAt) {
    }

    @FunctionalInterface
    interface WechatHttp {
        String exchange(String method, String url, String body);
    }

    private static final class RestWechatHttp implements WechatHttp {

        private final RestClient restClient = RestClient.builder().build();

        @Override
        public String exchange(String method, String url, String body) {
            try {
                URI uri = URI.create(url);
                if ("POST".equals(method)) {
                    return restClient.post()
                            .uri(uri)
                            .contentType(MediaType.APPLICATION_JSON)
                            .body(body == null ? "{}" : body)
                            .exchange((request, response) -> readBody(response));
                }
                return restClient.get().uri(uri).exchange((request, response) -> readBody(response));
            } catch (RestClientException ex) {
                log.warn("微信接口网络失败");
                throw new ViewerAuthException(400, "微信授权失败，请重试");
            }
        }

        private static String readBody(org.springframework.http.client.ClientHttpResponse response) throws java.io.IOException {
            return new String(response.getBody().readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
