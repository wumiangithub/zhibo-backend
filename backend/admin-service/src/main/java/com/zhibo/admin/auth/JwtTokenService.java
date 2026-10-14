package com.zhibo.admin.auth;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Date;
import java.util.HexFormat;
import javax.crypto.SecretKey;
import org.springframework.stereotype.Service;

/**
 * JWT 签发/校验。
 * <p>本站鉴权用 iss/aud/sub + base-admin 字段；同时写入 oms-go 业务员头所需的
 * {@code identifier}/{@code yhid}/{@code yg_name}（见对方「例子.go」），
 * 否则订单中心会报「登录态异常，请重新登录」。
 */
@Service
public class JwtTokenService {

    static final String TOKEN_TYPE_ACCESS = "access";
    static final String TOKEN_TYPE_REFRESH = "refresh";

    private final AuthProperties properties;
    private final Clock clock;
    private final SecureRandom secureRandom = new SecureRandom();

    public JwtTokenService(AuthProperties properties, Clock clock) {
        this.properties = properties;
        this.clock = clock;
    }

    public String issue(
            long userId,
            String userAccount,
            String userName,
            int userFlag,
            String identifier,
            String yhid) {
        return issue(
                userId,
                userAccount,
                userName,
                userFlag,
                identifier,
                yhid,
                accessAudience(),
                expireSeconds(),
                TOKEN_TYPE_ACCESS);
    }

    public String issueRefresh(
            long userId,
            String userAccount,
            String userName,
            int userFlag,
            String identifier,
            String yhid) {
        return issue(
                userId,
                userAccount,
                userName,
                userFlag,
                identifier,
                yhid,
                refreshAudience(),
                refreshExpireSeconds(),
                TOKEN_TYPE_REFRESH);
    }

    /** 业务接口用：必须是未过期的 access。refresh 或过期 access 都是 401。 */
    public long parseUserId(String token) {
        return subject(parse(token, accessAudience(), false, "未登录"));
    }

    /** 刷新接口用：access 过期放行，签名或 aud 不对不行。 */
    public long parseAccessAllowExpired(String token) {
        return subject(parse(token, accessAudience(), true, "登录凭证无效"));
    }

    /** 刷新接口用：refresh 必须未过期，且 aud 为 refresh。 */
    public long parseRefreshUserId(String token) {
        return subject(parse(token, refreshAudience(), false, "刷新凭证无效或已过期"));
    }

    public int expireSeconds() {
        return positiveSeconds(properties.getJwtExpireSeconds(), 7200);
    }

    public int refreshExpireSeconds() {
        return positiveSeconds(properties.getJwtRefreshExpireSeconds(), 604800);
    }

    private String issue(
            long userId,
            String userAccount,
            String userName,
            int userFlag,
            String identifier,
            String yhid,
            String audience,
            int expireSeconds,
            String tokenType) {
        if (properties.jwtSecretMissing()) {
            throw new IllegalStateException("未配置 JWT 密钥");
        }
        Instant now = clock.instant();
        Instant exp = now.plusSeconds(expireSeconds);
        String account = userAccount == null ? "" : userAccount;
        String name = userName == null || userName.isBlank() ? account : userName;
        String idClaim = identifier == null || identifier.isBlank() ? account : identifier.trim();
        String yhidClaim = yhid == null ? "0" : yhid.trim();
        return Jwts.builder()
                .issuer(properties.getJwtIssuer())
                .audience().add(audience).and()
                .subject(String.valueOf(userId))
                .id(randomJti())
                .claim("user_id", userId)
                .claim("user_account", account)
                .claim("user_name", name)
                .claim("user_flag", userFlag)
                .claim("token_type", tokenType)
                // oms-go Authorization-Live-Saler（例子.go SignSalerToken）
                .claim("identifier", idClaim)
                .claim("yhid", yhidClaim)
                .claim("yg_name", name)
                .issuedAt(Date.from(now))
                .notBefore(Date.from(now))
                .expiration(Date.from(exp))
                .signWith(signingKey())
                .compact();
    }

    private Claims parse(String token, String audience, boolean allowExpired, String failMessage) {
        if (token == null || token.isBlank() || properties.jwtSecretMissing()) {
            throw new AuthException(401, failMessage);
        }
        try {
            return Jwts.parser()
                    .verifyWith(signingKey())
                    .requireIssuer(properties.getJwtIssuer())
                    .requireAudience(audience)
                    .clock(() -> Date.from(clock.instant()))
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (ExpiredJwtException ex) {
            if (allowExpired && stillTheRightToken(ex.getClaims(), audience)) {
                return ex.getClaims();
            }
            throw new AuthException(401, failMessage);
        } catch (AuthException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new AuthException(401, failMessage);
        }
    }

    private boolean stillTheRightToken(Claims claims, String audience) {
        return claims != null
                && properties.getJwtIssuer().equals(claims.getIssuer())
                && claims.getAudience() != null
                && claims.getAudience().contains(audience);
    }

    private static long subject(Claims claims) {
        try {
            return Long.parseLong(claims.getSubject());
        } catch (RuntimeException ex) {
            throw new AuthException(401, "未登录");
        }
    }

    private String accessAudience() {
        String audience = properties.getJwtAudience();
        return audience == null || audience.isBlank() ? "base" : audience.trim();
    }

    private String refreshAudience() {
        String audience = properties.getJwtRefreshAudience();
        return audience == null || audience.isBlank() ? "base-refresh" : audience.trim();
    }

    private static int positiveSeconds(long value, int fallback) {
        if (value <= 0) {
            return fallback;
        }
        return (int) Math.min(value, Integer.MAX_VALUE);
    }

    private String randomJti() {
        byte[] value = new byte[16];
        secureRandom.nextBytes(value);
        return HexFormat.of().formatHex(value);
    }

    private SecretKey signingKey() {
        byte[] bytes = properties.getJwtSecret().getBytes(StandardCharsets.UTF_8);
        // Keys.hmacShaKeyFor 要求 ≥ 256 bit
        if (bytes.length < 32) {
            byte[] padded = new byte[32];
            System.arraycopy(bytes, 0, padded, 0, bytes.length);
            bytes = padded;
        }
        return Keys.hmacShaKeyFor(bytes);
    }
}
