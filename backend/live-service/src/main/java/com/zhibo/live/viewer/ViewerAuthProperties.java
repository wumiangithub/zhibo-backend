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

    /** 用户中心（钱坤云）微信注册/登录；按环境配 Base URL（D116）。 */
    private Oauth oauth = new Oauth();

    @Data
    public static class Wechat {
        private String mpAppId = "";
        private String mpAppSecret = "";
    }

    @Data
    public static class Oauth {
        /** 用户中心根地址，如 {@code http://119.3.31.54:8410/acctClientManagement/}（无票代理）。缺则正式微信登录不可用。 */
        private String baseUrl = "";

        /** 注册渠道文案；默认「钱坤云私域直播用户注册」。 */
        private String channel = "钱坤云私域直播用户注册";

        /** 项目名称；默认「钱坤云私域直播」。 */
        private String projectName = "钱坤云私域直播";

        /** 项目编码；默认 {@code qkysyzb}。 */
        private String projectCode = "qkysyzb";
    }

    public boolean jwtSecretMissing() {
        return isBlank(jwtSecret);
    }

    public boolean wechatCredentialsMissing() {
        Wechat wx = wechat == null ? null : wechat;
        return wx == null || isBlank(wx.mpAppId) || isBlank(wx.mpAppSecret);
    }

    public boolean oauthBaseUrlMissing() {
        return oauth == null || isBlank(oauth.baseUrl);
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
