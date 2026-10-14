package com.zhibo.admin.auth;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 管理端登录配置（D41）。
 * <p>
 * 绑定 {@code auth.*} / {@code wechat.*}（.env 的 {@code AUTH_*} / {@code WECHAT_*} 经 dotenv 注入）。
 * 缺密钥不拒启动；调登录接口时再校验。
 */
@Data
@ConfigurationProperties(prefix = "auth")
public class AuthProperties {

    /** AES-256 密钥的 Base64（32 字节）。 */
    private String passwordAesKeyBase64 = "";

    /** JWT HMAC 密钥。 */
    private String jwtSecret = "";

    /** access 有效秒数；默认对齐旧 base-admin AccessExpire=7200。 */
    private long jwtExpireSeconds = 7200;

    /** refresh 有效秒数；默认 7 天。 */
    private long jwtRefreshExpireSeconds = 604800;

    /** JWT iss；默认对齐旧 base-admin Issuer。 */
    private String jwtIssuer = "base-admin-api";

    /** access 的 aud；默认对齐旧 base-admin Audience。 */
    private String jwtAudience = "base";

    /** refresh 的 aud。放进业务 Authorization 时必须被拒绝。 */
    private String jwtRefreshAudience = "base-refresh";

    private Wechat wechat = new Wechat();

    @Data
    public static class Wechat {
        private String openAppId = "";
        private String openAppSecret = "";
        private String openRedirectUri = "";

        /**
         * 本地开发临时开关：未绑定账号扫码后写库绑定 openid/unionid。
         * 默认 false（正式：未绑定仍提示联系管理员）。勿在生产开启。
         */
        private boolean allowSelfBind = false;
    }

    public boolean passwordAesKeyMissing() {
        return isBlank(passwordAesKeyBase64);
    }

    public boolean jwtSecretMissing() {
        return isBlank(jwtSecret);
    }

    public boolean wechatCredentialsMissing() {
        return isBlank(wechat.openAppId)
                || isBlank(wechat.openAppSecret)
                || isBlank(wechat.openRedirectUri);
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
