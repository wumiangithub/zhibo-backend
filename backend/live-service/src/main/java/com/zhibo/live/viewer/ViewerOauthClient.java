package com.zhibo.live.viewer;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * 用户中心：通过 unionid 注册/登录微信用户（{@code POST /oauth/wechat/register}）。
 * 本轮裸调（无签名）；只取 {@code uid}/{@code username} 写入本站。
 */
@Slf4j
@Component
public class ViewerOauthClient {

    private static final String PATH = "/oauth/wechat/register";
    private static final String LOGIN_FAIL = "登录失败，请重试";

    private final ViewerAuthProperties properties;
    private final ObjectMapper objectMapper;
    private final OauthHttp http;

    @Autowired
    public ViewerOauthClient(ViewerAuthProperties properties, ObjectMapper objectMapper) {
        this(properties, objectMapper, new RestOauthHttp());
    }

    ViewerOauthClient(ViewerAuthProperties properties, ObjectMapper objectMapper, OauthHttp http) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.http = http;
    }

    /** 用户中心登录成功后的跨系统标识。 */
    public record OauthIdentity(long uid, String username) {
    }

    /**
     * 用微信 unionId 向用户中心注册或登录。
     *
     * @param unionId 微信 unionid（必填）
     * @param deviceType {@code phone} 或 {@code pc}
     */
    public OauthIdentity registerByUnionId(String unionId, String deviceType) {
        requireConfigured();
        if (unionId == null || unionId.isBlank()) {
            throw new ViewerAuthException(400, "微信授权失败，请重试");
        }
        String type = normalizeType(deviceType);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("unionId", unionId.trim());
        body.put("channel", channel());
        body.put("projectName", projectName());
        body.put("projectCode", projectCode());
        body.put("type", type);
        // nickname 不传

        String json;
        try {
            json = objectMapper.writeValueAsString(body);
        } catch (Exception ex) {
            log.warn("用户中心注册请求序列化失败");
            throw new ViewerAuthException(400, LOGIN_FAIL);
        }

        String raw;
        try {
            raw = http.postJson(registerUrl(), json);
        } catch (ViewerAuthException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            log.warn("用户中心注册网络失败");
            throw new ViewerAuthException(400, LOGIN_FAIL);
        }

        JsonNode root = read(raw);
        int code = root.path("code").asInt(Integer.MIN_VALUE);
        if (code != 0 && code != 200) {
            String remoteMsg = text(root, "message");
            if (remoteMsg == null) {
                remoteMsg = text(root, "msg");
            }
            log.warn(
                    "用户中心注册业务失败 code={} msg={} bodySnippet={}",
                    code,
                    remoteMsg,
                    snippet(raw));
            throw new ViewerAuthException(400, LOGIN_FAIL);
        }
        JsonNode data = root.path("data");
        if (data == null || data.isMissingNode() || data.isNull()) {
            log.warn("用户中心注册无 data");
            throw new ViewerAuthException(400, LOGIN_FAIL);
        }
        int status = data.path("status").asInt(0);
        if (status != 0) {
            log.warn("用户中心账号不可用 status={}", status);
            throw new ViewerAuthException(400, LOGIN_FAIL);
        }
        if (!data.hasNonNull("uid") || !data.get("uid").canConvertToLong()) {
            log.warn("用户中心注册缺少 uid");
            throw new ViewerAuthException(400, LOGIN_FAIL);
        }
        long uid = data.get("uid").asLong();
        String username = text(data, "username");
        if (username == null || username.length() > 64) {
            log.warn("用户中心注册缺少 username");
            throw new ViewerAuthException(400, LOGIN_FAIL);
        }
        return new OauthIdentity(uid, username.trim());
    }

    private void requireConfigured() {
        if (baseUrl().isBlank()) {
            log.warn("观众登录配置缺失: VIEWER_OAUTH_BASE_URL");
            throw new ViewerAuthException(500, "观众登录暂不可用");
        }
    }

    private String registerUrl() {
        String base = baseUrl();
        if (base.endsWith("/")) {
            return base.substring(0, base.length() - 1) + PATH;
        }
        return base + PATH;
    }

    private String baseUrl() {
        ViewerAuthProperties.Oauth oauth = properties.getOauth();
        if (oauth == null || oauth.getBaseUrl() == null) {
            return "";
        }
        return oauth.getBaseUrl().trim();
    }

    private String channel() {
        ViewerAuthProperties.Oauth oauth = properties.getOauth();
        String value = oauth == null ? null : oauth.getChannel();
        return value == null || value.isBlank() ? "钱坤云私域直播用户注册" : value.trim();
    }

    private String projectName() {
        ViewerAuthProperties.Oauth oauth = properties.getOauth();
        String value = oauth == null ? null : oauth.getProjectName();
        return value == null || value.isBlank() ? "钱坤云私域直播" : value.trim();
    }

    private String projectCode() {
        ViewerAuthProperties.Oauth oauth = properties.getOauth();
        String value = oauth == null ? null : oauth.getProjectCode();
        return value == null || value.isBlank() ? "qkysyzb" : value.trim();
    }

    private static String normalizeType(String deviceType) {
        if (deviceType == null) {
            throw new ViewerAuthException(400, "缺少设备类型");
        }
        String type = deviceType.trim().toLowerCase();
        if (!"phone".equals(type) && !"pc".equals(type)) {
            throw new ViewerAuthException(400, "设备类型须为 phone 或 pc");
        }
        return type;
    }

    private JsonNode read(String body) {
        try {
            return objectMapper.readTree(body == null || body.isBlank() ? "{}" : body);
        } catch (Exception ex) {
            log.warn("用户中心响应无法解析");
            throw new ViewerAuthException(400, LOGIN_FAIL);
        }
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

    private static String snippet(String raw) {
        if (raw == null || raw.isBlank()) {
            return "";
        }
        String trimmed = raw.trim().replaceAll("\\s+", " ");
        return trimmed.length() <= 240 ? trimmed : trimmed.substring(0, 240);
    }

    @FunctionalInterface
    interface OauthHttp {
        String postJson(String url, String body);
    }

    private static final class RestOauthHttp implements OauthHttp {

        private final RestClient restClient = RestClient.builder().build();

        @Override
        public String postJson(String url, String body) {
            try {
                return restClient.post()
                        .uri(URI.create(url))
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(body == null ? "{}" : body)
                        .exchange((request, response) ->
                                new String(response.getBody().readAllBytes(), StandardCharsets.UTF_8));
            } catch (RestClientException ex) {
                log.warn("用户中心接口网络失败");
                throw new ViewerAuthException(400, LOGIN_FAIL);
            }
        }
    }
}
