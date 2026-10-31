package com.zhibo.live.viewer;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 观众登录配置（D73）。
 * <p>
 * 绑定 {@code viewer.*}。.env 的 {@code VIEWER_*} / {@code WECHAT_MP_*} 经 dotenv 注入。
 * 缺密钥不拒启动；调登录接口时再返回业务错误。
 */
@Data
@ConfigurationProperties(prefix = "viewer")
public class ViewerAuthProperties {

    /** 观众 JWT HMAC 密钥。不要复用管理端 AUTH_JWT_SECRET。 */
    private String jwtSecret = "";

    /** access 有效秒数，默认 2 小时。 */
    private long jwtExpireSeconds = 7200;

    /** refresh 有效秒数，默认 7 天。 */
    private long jwtRefreshExpireSeconds = 604800;

    /** JWT iss。 */
    private String jwtIssuer = "zhibo-viewer";

    /** access 的 aud。管理端 aud=base 不能过观众接口。 */
    private String jwtAudience = "viewer";

    /** refresh 的 aud。放进 Authorization 时必须被拒绝。 */
    private String jwtRefreshAudience = "viewer-refresh";

    /** 加密售卖本场访问 JWT 的 aud（D90）；勿与 viewer / viewer-refresh 混用。 */
    private String activityAccessAudience = "activity-access";

    /** 本场访问 JWT 默认有效秒数（24h）；若访问截止更早则截断。 */
    private long activityAccessExpireSeconds = 86400;

    /** 微信小程序这一个渠道的 AppID / AppSecret。 */
    private Wechat wechat = new Wechat();

    @Data
    public static class Wechat {
        private String mpAppId = "";
        private String mpAppSecret = "";
    }

    public boolean jwtSecretMissing() {
        return isBlank(jwtSecret);
    }

    public boolean wechatCredentialsMissing() {
        Wechat wx = wechat == null ? null : wechat;
        return wx == null || isBlank(wx.mpAppId) || isBlank(wx.mpAppSecret);
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
