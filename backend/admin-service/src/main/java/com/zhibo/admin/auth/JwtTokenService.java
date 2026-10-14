package com.zhibo.admin.auth;

import io.jsonwebtoken.Claims;
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
        if (properties.jwtSecretMissing()) {
            throw new IllegalStateException("未配置 JWT 密钥");
        }
        Instant now = clock.instant();
        Instant exp = now.plusSeconds(properties.getJwtExpireSeconds());
        return Jwts.builder()
                .issuer(properties.getJwtIssuer())
                .audience().add(properties.getJwtAudience()).and()
                .subject(String.valueOf(userId))
                .claim("userAccount", userAccount)
                .issuedAt(Date.from(now))
                .expiration(Date.from(exp))
                .signWith(signingKey())
                .compact();
    }

    /** 校验并返回 userId；无效则抛 {@link AuthException} 401。 */
    public long parseUserId(String token) {
        if (token == null || token.isBlank()) {
            throw new AuthException(401, "未登录");
        }
        if (properties.jwtSecretMissing()) {
            throw new AuthException(401, "未登录");
        }
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(signingKey())
                    .requireIssuer(properties.getJwtIssuer())
                    .requireAudience(properties.getJwtAudience())
                    .clock(() -> Date.from(clock.instant()))
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            return Long.parseLong(claims.getSubject());
        } catch (AuthException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new AuthException(401, "未登录");
        }
    }

    public int expireSeconds() {
        long v = properties.getJwtExpireSeconds();
        if (v <= 0) {
            return 7200;
        }
        return (int) Math.min(v, Integer.MAX_VALUE);
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
