package com.zhibo.live.watch;

import com.zhibo.live.viewer.ViewerAuthProperties;
import io.jsonwebtoken.Claims;
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
 * 加密售卖本场访问 JWT（D90）。aud=activity-access，sub=活动 UUID；密钥复用 VIEWER_JWT_SECRET。
 */
@Slf4j
@Service
public class ActivityAccessTokenService {

    static final String TOKEN_TYPE = "activity_access";

    private final ViewerAuthProperties properties;
    private final Clock clock;
    private final SecureRandom secureRandom = new SecureRandom();

    public ActivityAccessTokenService(ViewerAuthProperties properties, Clock clock) {
        this.properties = properties;
        this.clock = clock;
    }

    public String issue(String activityId, int expireSeconds) {
        if (properties.jwtSecretMissing()) {
            log.warn("观众登录配置缺失: VIEWER_JWT_SECRET（本场访问 JWT 不可用）");
            throw new WatchAccessException(500, "观看访问凭证暂不可用");
        }
        int seconds = Math.max(60, expireSeconds);
        Instant now = clock.instant();
        Instant exp = now.plusSeconds(seconds);
        return Jwts.builder()
                .issuer(issuer())
                .audience().add(audience()).and()
                .subject(activityId)
                .id(randomJti())
                .claim("token_type", TOKEN_TYPE)
                .issuedAt(Date.from(now))
                .notBefore(Date.from(now))
                .expiration(Date.from(exp))
                .signWith(signingKey())
                .compact();
    }

    public int defaultExpireSeconds() {
        long v = properties.getActivityAccessExpireSeconds();
        if (v <= 0) {
            return 86400;
        }
        return (int) Math.min(v, Integer.MAX_VALUE);
    }

    /** 校验本场访问 JWT；不匹配或无效 → false（不抛）。 */
    public boolean isValidForActivity(String token, String activityId) {
        if (token == null || token.isBlank() || activityId == null || activityId.isBlank()) {
            return false;
        }
        if (properties.jwtSecretMissing()) {
            return false;
        }
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(signingKey())
                    .requireIssuer(issuer())
                    .requireAudience(audience())
                    .clock(() -> Date.from(clock.instant()))
                    .build()
                    .parseSignedClaims(token.trim())
                    .getPayload();
            if (!TOKEN_TYPE.equals(claims.get("token_type", String.class))) {
                return false;
            }
            return activityId.equals(claims.getSubject());
        } catch (Exception ex) {
            return false;
        }
    }

    private String issuer() {
        String issuer = properties.getJwtIssuer();
        return issuer == null || issuer.isBlank() ? "zhibo-viewer" : issuer.trim();
    }

    private String audience() {
        String audience = properties.getActivityAccessAudience();
        return audience == null || audience.isBlank() ? "activity-access" : audience.trim();
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
