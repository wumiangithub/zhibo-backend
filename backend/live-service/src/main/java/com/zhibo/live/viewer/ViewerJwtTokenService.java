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
 * 观众 JWT。只放观众 id，不抄员工票里的 oms 字段。
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

    public String issueAccess(long viewerId) {
        return issue(viewerId, accessAudience(), expireSeconds(), TOKEN_TYPE_ACCESS);
    }

    public String issueRefresh(long viewerId) {
        return issue(viewerId, refreshAudience(), refreshExpireSeconds(), TOKEN_TYPE_REFRESH);
    }

    /** 业务接口：必须是未过期的 access。refresh 或过期 access 都是 401。 */
    public long parseUserId(String token) {
        return subject(parse(token, accessAudience(), false, TOKEN_TYPE_ACCESS, "未登录"));
    }

    /** 刷新：access 过期放行，签名或 aud 不对不行。 */
    public long parseAccessAllowExpired(String token) {
        return subject(parse(token, accessAudience(), true, TOKEN_TYPE_ACCESS, "登录状态已失效，请重新登录"));
    }

    /** 刷新：refresh 必须未过期，且 aud 为 viewer-refresh。 */
    public long parseRefreshUserId(String token) {
        return subject(parse(token, refreshAudience(), false, TOKEN_TYPE_REFRESH, "刷新凭证无效或已过期"));
    }

    public int expireSeconds() {
        return positiveSeconds(properties.getJwtExpireSeconds(), 7200);
    }

    public int refreshExpireSeconds() {
        return positiveSeconds(properties.getJwtRefreshExpireSeconds(), 604800);
    }

    private String issue(long viewerId, String audience, int expireSeconds, String tokenType) {
        if (properties.jwtSecretMissing()) {
            log.warn("观众登录配置缺失: VIEWER_JWT_SECRET");
            throw new ViewerAuthException(500, "观众登录暂不可用");
        }
        Instant now = clock.instant();
        Instant exp = now.plusSeconds(expireSeconds);
        return Jwts.builder()
                .issuer(issuer())
                .audience().add(audience).and()
                .subject(String.valueOf(viewerId))
                .id(randomJti())
                .claim("token_type", tokenType)
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

    private static long subject(Claims claims) {
        try {
            return Long.parseLong(claims.getSubject());
        } catch (RuntimeException ex) {
            throw new ViewerAuthException(401, "未登录");
        }
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
