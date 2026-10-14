package com.zhibo.admin.auth;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.Date;
import javax.crypto.SecretKey;
import org.springframework.stereotype.Service;

@Service
public class JwtTokenService {

    private final AuthProperties properties;
    private final Clock clock;

    public JwtTokenService(AuthProperties properties, Clock clock) {
        this.properties = properties;
        this.clock = clock;
    }

    public String issue(long userId, String userAccount) {
        return issue(userId, userAccount, accessAudience(), expireSeconds());
    }

    public String issueRefresh(long userId, String userAccount) {
        return issue(userId, userAccount, refreshAudience(), refreshExpireSeconds());
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

    private String issue(long userId, String userAccount, String audience, int expireSeconds) {
        if (properties.jwtSecretMissing()) {
            throw new IllegalStateException("未配置 JWT 密钥");
        }
        Instant now = clock.instant();
        Instant exp = now.plusSeconds(expireSeconds);
        return Jwts.builder()
                .issuer(properties.getJwtIssuer())
                .audience().add(audience).and()
                .subject(String.valueOf(userId))
                .claim("userAccount", userAccount)
                .issuedAt(Date.from(now))
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
