package com.zhibo.live.watch;

import com.zhibo.live.watch.dto.SdkPayload;
import com.zhibo.live.watch.dto.WatchSdkResponse;
import com.zhibo.vhall.client.VhallClient;
import com.zhibo.vhall.config.VhallProperties;
import com.zhibo.vhall.util.VhallSignUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 观看端 SDK 3.9.1 初始化载荷。
 * <p>
 * 文档：<a href="https://vhall.apifox.cn/dev/sdk/jssdk/quick-start">JSSDK 接入必读</a>
 * <p>
 * 签名参与字段（仅这些；勿把 sign / sign_type 签进去）：
 * {@code app_key}、{@code signedat}、{@code webinar_id}、{@code email}、{@code username}；
 * 若传 {@code head} 也参与签名。{@code email} 与 {@code account} 互斥，本项目只用 email。
 * <p>
 * 算法与 {@link VhallSignUtil} 相同：参数名升序 → 名+值拼接 → 首尾加 secret → MD5 小写。
 * {@code signedat} 官方约 5 分钟有效。
 */
@Service
@RequiredArgsConstructor
public class WatchSdkService {

    static final String PATH_INFO = "/v3/webinars/webinar/info";
    static final String SDK_SCRIPT_URL = "https://cnstatic01.e.vhall.com/jssdk/dist/3.9.1/VhallSDK.js";

    private final VhallClient vhallClient;
    private final VhallProperties properties;
    private final Clock clock;

    public WatchSdkResponse sdk(long activityId, String guestId, String nickname) {
        if (activityId <= 0) {
            throw new IllegalArgumentException("活动 id 无效");
        }
        String appSecret = properties.getAppSecret();
        if (appSecret == null || appSecret.isBlank()) {
            throw new IllegalStateException("缺少微吼 SecretKey，无法签名 SDK 载荷");
        }

        String resolvedGuestId = WatchIdentity.resolveGuestId(guestId);
        String resolvedNickname = WatchIdentity.resolveNickname(nickname);
        String email = WatchIdentity.emailFor(resolvedGuestId);
        String webinarId = Long.toString(activityId);

        @SuppressWarnings("unchecked")
        Map<String, Object> info = vhallClient.postForData(
                PATH_INFO, Map.of("webinar_id", activityId), Map.class);

        String baseEmbed = asString(info.get("full_embed_share_link"));
        if (baseEmbed == null || baseEmbed.isBlank()) {
            throw new IllegalStateException("活动缺少全屏嵌入地址 full_embed_share_link");
        }
        String embedUrl = WatchIdentity.buildEmbedUrl(baseEmbed, resolvedGuestId, resolvedNickname);

        String signedAt = Long.toString(clock.instant().getEpochSecond());
        // 3.9.1：只用 email，不签 account；无 head
        Map<String, Object> signParams = new LinkedHashMap<>();
        signParams.put("app_key", properties.getAppKey());
        signParams.put("signedat", signedAt);
        signParams.put("webinar_id", webinarId);
        signParams.put("email", email);
        signParams.put("username", resolvedNickname);
        String sign = VhallSignUtil.sign(appSecret, signParams);

        SdkPayload sdk = new SdkPayload();
        sdk.setScriptUrl(SDK_SCRIPT_URL);
        sdk.setJqueryUrl(null);
        sdk.setAccount(resolvedGuestId);
        sdk.setEmail(email);
        sdk.setUsername(resolvedNickname);
        sdk.setWebinarId(webinarId);
        sdk.setAppKey(properties.getAppKey());
        sdk.setSignedAt(signedAt);
        sdk.setSignType(0);
        sdk.setSign(sign);

        WatchSdkResponse response = new WatchSdkResponse();
        response.setId(activityId);
        response.setTitle(asString(info.get("subject")));
        response.setState(asInteger(info.get("webinar_state")));
        response.setType(asInteger(info.get("webinar_type")));
        response.setNickname(resolvedNickname);
        response.setGuestId(resolvedGuestId);
        response.setSdk(sdk);
        response.setEmbedUrl(embedUrl);
        return response;
    }

    private static String asString(Object value) {
        return value == null ? null : value.toString();
    }

    private static Integer asInteger(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return Integer.parseInt(value.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
