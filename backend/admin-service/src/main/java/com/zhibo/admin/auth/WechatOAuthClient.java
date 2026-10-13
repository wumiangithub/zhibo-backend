package com.zhibo.admin.auth;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.util.UriComponentsBuilder;

/** 微信开放平台网站应用：code 换 openid/unionid。 */
@Slf4j
@Component
public class WechatOAuthClient {

    private final AuthProperties properties;
    private final ObjectMapper objectMapper;
    private final RestClient restClient;

    public WechatOAuthClient(AuthProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.restClient = RestClient.builder().build();
    }

    public record WechatIdentity(String openid, String unionid) {}

    public WechatIdentity exchangeCode(String code) {
        if (properties.wechatCredentialsMissing()) {
            throw new IllegalStateException("未配置微信开放平台凭证");
        }
        String url = UriComponentsBuilder
                .fromUriString("https://api.weixin.qq.com/sns/oauth2/access_token")
                .queryParam("appid", properties.getWechat().getOpenAppId())
                .queryParam("secret", properties.getWechat().getOpenAppSecret())
                .queryParam("code", code)
                .queryParam("grant_type", "authorization_code")
                .build(true)
                .toUriString();
        try {
            String body = restClient.get().uri(url).retrieve().body(String.class);
            JsonNode node = objectMapper.readTree(body == null ? "{}" : body);
            if (node.hasNonNull("errcode") && node.get("errcode").asInt() != 0) {
                log.warn("微信换票失败 errcode={} errmsg={}",
                        node.get("errcode").asInt(),
                        node.path("errmsg").asText());
                throw new IllegalArgumentException("微信授权失败，请重试");
            }
            String openid = text(node, "openid");
            if (openid == null || openid.isBlank()) {
                throw new IllegalArgumentException("微信授权失败，请重试");
            }
            String unionid = text(node, "unionid");
            return new WechatIdentity(openid, unionid);
        } catch (IllegalArgumentException ex) {
            throw ex;
        } catch (RestClientException ex) {
            log.warn("微信换票网络失败: {}", ex.getMessage());
            throw new IllegalStateException("微信授权失败，请重试");
        } catch (Exception ex) {
            log.warn("微信换票解析失败: {}", ex.getMessage());
            throw new IllegalStateException("微信授权失败，请重试");
        }
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) {
            return null;
        }
        String s = value.asText();
        return s == null || s.isBlank() ? null : s;
    }
}
