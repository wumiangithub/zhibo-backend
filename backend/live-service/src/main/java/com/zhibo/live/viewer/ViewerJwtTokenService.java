package com.zhibo.live.viewer;

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
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 观众 JWT。本站鉴权用 iss/aud/sub + token_type；{@code sub} = 用户中心 username（D118）。
 * 同时写入 oms-go 的 {@code username}/{@code third_user_id}（=username）。
 * access 的 aud 是 viewer，refresh 的 aud 是 viewer-refresh。
 */
@Slf4j
@Service
public class ViewerJwtTokenService {

    static final String TOKEN_TYPE_ACCESS = "access";
    static final String TOKEN_TYPE_REFRESH = "refresh";

    private final ViewerAuthProperties properties;
    private final Clock clock;
    private final SecureRandom secureRandom = new SecureRandom();

    public ViewerJwtTokenService(ViewerAuthProperties properties, Clock clock) {
        this.properties = properties;
        this.clock = clock;
    }

    /**
     * @param username 用户中心 username；正式登录必有。local 无 username 时传 null/空 → sub 写空（不可写业务表）
     */
    public String issueAccess(String username) {
        return issue(username, accessAudience(), expireSeconds(), TOKEN_TYPE_ACCESS);
    }

    public String issueRefresh(String username) {
        return issue(username, refreshAudience(), refreshExpireSeconds(), TOKEN_TYPE_REFRESH);
    }

    /** 业务接口：必须是未过期的 access。返回 username（JWT sub）。 */
    public String parseUsername(String token) {
        return subject(parse(token, accessAudience(), false, TOKEN_TYPE_ACCESS, "未登录"));
    }

    /** @deprecated 用 {@link #parseUsername}。 */
    @Deprecated
    public long parseUid(String token) {
        String username = parseUsername(token);
        try {
            return Long.parseLong(username);
        } catch (RuntimeException ex) {
            throw new ViewerAuthException(401, "未登录");
        }
    }

    /** @deprecated 用 {@link #parseUsername}。 */
    @Deprecated
    public long parseUserId(String token) {
        return parseUid(token);
    }

    /** 刷新：access 过期放行，签名或 aud 不对不行。 */
    public String parseAccessAllowExpired(String token) {
        return subject(parse(token, accessAudience(), true, TOKEN_TYPE_ACCESS, "登录状态已失效，请重新登录"));
    }

    /** 刷新：refresh 必须未过期，且 aud 为 viewer-refresh。 */
    public String parseRefreshUsername(String token) {
        return subject(parse(token, refreshAudience(), false, TOKEN_TYPE_REFRESH, "刷新凭证无效或已过期"));
    }

    /** @deprecated 用 {@link #parseRefreshUsername}。 */
    @Deprecated
    public long parseRefreshUid(String token) {
        String username = parseRefreshUsername(token);
        try {
            return Long.parseLong(username);
        } catch (RuntimeException ex) {
            throw new ViewerAuthException(401, "刷新凭证无效或已过期");
        }
    }

    /** @deprecated 用 {@link #parseRefreshUsername}。 */
    @Deprecated
    public long parseRefreshUserId(String token) {
        return parseRefreshUid(token);
    }

    public int expireSeconds() {
        return positiveSeconds(properties.getJwtExpireSeconds(), 7200);
    }

    public int refreshExpireSeconds() {
        return positiveSeconds(properties.getJwtRefreshExpireSeconds(), 604800);
    }

    private String issue(String username, String audience, int expireSeconds, String tokenType) {
        if (properties.jwtSecretMissing()) {
            log.warn("观众登录配置缺失: VIEWER_JWT_SECRET");
            throw new ViewerAuthException(500, "观众登录暂不可用");
        }
        Instant now = clock.instant();
        Instant exp = now.plusSeconds(expireSeconds);
        String identity = username == null ? "" : username.trim();
        String thirdUserId = identity;
        return Jwts.builder()
                .issuer(issuer())
                .audience().add(audience).and()
                .subject(identity)
                .id(randomJti())
                .claim("token_type", tokenType)
                .claim("username", identity)
                .claim("third_user_id", thirdUserId)
                .issuedAt(Date.from(now))
                .notBefore(Date.from(now))
                .expiration(Date.from(exp))
                .signWith(signingKey())
                .compact();
    }

    private Claims parse(
            String token,
            String audience,
            boolean allowExpired,
            String tokenType,
            String failMessage) {
        if (token == null || token.isBlank() || properties.jwtSecretMissing()) {
            if (properties.jwtSecretMissing()) {
                log.warn("观众登录配置缺失: VIEWER_JWT_SECRET");
            }
            throw new ViewerAuthException(401, failMessage);
        }
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(signingKey())
                    .requireIssuer(issuer())
                    .requireAudience(audience)
                    .clock(() -> Date.from(clock.instant()))
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            return requireType(claims, tokenType, failMessage);
        } catch (ExpiredJwtException ex) {
            if (allowExpired && stillTheRightToken(ex.getClaims(), audience)) {
                return requireType(ex.getClaims(), tokenType, failMessage);
            }
            throw new ViewerAuthException(401, failMessage);
        } catch (ViewerAuthException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new ViewerAuthException(401, failMessage);
        }
    }

    private Claims requireType(Claims claims, String tokenType, String failMessage) {
        if (claims == null || !tokenType.equals(claims.get("token_type", String.class))) {
            throw new ViewerAuthException(401, failMessage);
        }
        return claims;
    }

    private boolean stillTheRightToken(Claims claims, String audience) {
        return claims != null
                && issuer().equals(claims.getIssuer())
                && claims.getAudience() != null
                && claims.getAudience().contains(audience);
    }

    private static String subject(Claims claims) {
        String sub = claims == null ? null : claims.getSubject();
        if (sub == null || sub.isBlank()) {
            throw new ViewerAuthException(401, "未登录");
        }
        return sub.trim();
    }

    private String issuer() {
        String issuer = properties.getJwtIssuer();
        return issuer == null || issuer.isBlank() ? "zhibo-viewer" : issuer.trim();
    }

    private String accessAudience() {
        String audience = properties.getJwtAudience();
        return audience == null || audience.isBlank() ? "viewer" : audience.trim();
    }

    private String refreshAudience() {
        String audience = properties.getJwtRefreshAudience();
        return audience == null || audience.isBlank() ? "viewer-refresh" : audience.trim();
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
        if (bytes.length < 32) {
            byte[] padded = new byte[32];
            System.arraycopy(bytes, 0, padded, 0, bytes.length);
            bytes = padded;
        }
        return Keys.hmacShaKeyFor(bytes);
    }
}
