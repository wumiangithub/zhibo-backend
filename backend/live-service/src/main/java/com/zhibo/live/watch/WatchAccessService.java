package com.zhibo.live.watch;

import com.zhibo.activity.AccessRules;
import com.zhibo.activity.ActivityAudienceRepository;
import com.zhibo.activity.ActivityEntity;
import com.zhibo.activity.SellPasswordCryptoService;
import com.zhibo.live.watch.dto.VerifyPasswordResponse;
import com.zhibo.live.watch.dto.WatchAccessResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;

/**
 * 观看售卖门禁（D90）。
 */
@Service
@RequiredArgsConstructor
public class WatchAccessService {

    public static final String HEADER_ACTIVITY_ACCESS = "X-Activity-Access";

    static final ZoneId SHANGHAI = ZoneId.of("Asia/Shanghai");

    public static final String STATE_OK = "ok";
    public static final String STATE_NEED_PASSWORD = "need_password";
    public static final String STATE_NEED_LOGIN = "need_login";
    public static final String STATE_FORBIDDEN = "forbidden";
    public static final String STATE_EXPIRED = "expired";

    private final WatchService watchService;
    private final ActivityAudienceRepository audienceRepository;
    private final SellPasswordCryptoService sellPasswordCrypto;
    private final ActivityAccessTokenService activityAccessTokenService;
    private final Clock clock;

    public WatchAccessResponse access(String activityId, Long viewerId, String activityAccessToken) {
        ActivityEntity entity = watchService.requireActivity(activityId);
        watchService.requireReadyForWatch(entity);
        return buildResponse(entity, resolveState(entity, viewerId, activityAccessToken));
    }

    public VerifyPasswordResponse verifyPassword(String activityId, String password) {
        ActivityEntity entity = watchService.requireActivity(activityId);
        watchService.requireReadyForWatch(entity);
        if (!Boolean.TRUE.equals(entity.getSellAlone()) || !"encrypted".equals(entity.getSellType())) {
            throw new IllegalArgumentException("本场无需密码");
        }
        LocalDateTime now = now();
        if (AccessRules.isAccessExpired(entity, now)) {
            throw new WatchAccessException("访问已过期");
        }
        String raw = password == null ? "" : password.trim();
        if (!raw.matches("\\d{6}")) {
            throw new IllegalArgumentException("请输入6位数字密码");
        }
        String cipher = entity.getSellPasswordCipher();
        if (cipher == null || cipher.isBlank()) {
            throw new IllegalArgumentException("本场未设置密码");
        }
        String plain;
        try {
            plain = sellPasswordCrypto.decrypt(cipher);
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("密码错误");
        }
        if (!raw.equals(plain)) {
            throw new IllegalArgumentException("密码错误");
        }
        int expiresIn = resolveExpireSeconds(entity, now);
        String token = activityAccessTokenService.issue(entity.getId(), expiresIn);
        return new VerifyPasswordResponse(token, expiresIn);
    }

    /** sdk / records / replay 硬拦；未放行抛 403。 */
    public void assertAllowed(ActivityEntity entity, Long viewerId, String activityAccessToken) {
        String state = resolveState(entity, viewerId, activityAccessToken);
        if (STATE_OK.equals(state)) {
            return;
        }
        throw new WatchAccessException(messageFor(state));
    }

    public void assertAllowed(String activityId, Long viewerId, String activityAccessToken) {
        ActivityEntity entity = watchService.requireActivity(activityId);
        watchService.requireReadyForWatch(entity);
        assertAllowed(entity, viewerId, activityAccessToken);
    }

    String resolveState(ActivityEntity entity, Long viewerId, String activityAccessToken) {
        LocalDateTime now = now();
        if (!AccessRules.requiresGate(entity)) {
            return STATE_OK;
        }
        if (AccessRules.isAccessExpired(entity, now)) {
            return STATE_EXPIRED;
        }
        String sellType = entity.getSellType();
        if ("encrypted".equals(sellType)) {
            if (activityAccessTokenService.isValidForActivity(activityAccessToken, entity.getId())) {
                return STATE_OK;
            }
            return STATE_NEED_PASSWORD;
        }
        if ("specified".equals(sellType)) {
            if (viewerId == null || viewerId <= 0) {
                return STATE_NEED_LOGIN;
            }
            return audienceRepository.findByActivityIdAndViewerId(entity.getId(), viewerId)
                    .filter(row -> row.getEntitlementStatus() == 1)
                    .map(row -> STATE_OK)
                    .orElse(STATE_FORBIDDEN);
        }
        return STATE_OK;
    }

    private WatchAccessResponse buildResponse(ActivityEntity entity, String state) {
        WatchAccessResponse response = new WatchAccessResponse();
        response.setId(entity.getId());
        response.setSellAlone(Boolean.TRUE.equals(entity.getSellAlone()));
        response.setSellType(entity.getSellType() != null ? entity.getSellType() : "free");
        response.setAccessState(state);
        response.setAccessExpired(STATE_EXPIRED.equals(state)
                || AccessRules.isAccessExpired(entity, now()));
        response.setAccessValidityType(
                entity.getAccessValidityType() != null ? entity.getAccessValidityType() : "long");
        response.setAccessValidityDays(entity.getAccessValidityDays());
        return response;
    }

    private int resolveExpireSeconds(ActivityEntity entity, LocalDateTime now) {
        int def = activityAccessTokenService.defaultExpireSeconds();
        if (!"days".equals(entity.getAccessValidityType())
                || entity.getLiveActualEndTime() == null
                || entity.getAccessValidityDays() == null
                || entity.getAccessValidityDays() < 1) {
            return def;
        }
        LocalDateTime deadline = entity.getLiveActualEndTime().plusDays(entity.getAccessValidityDays());
        long seconds = ChronoUnit.SECONDS.between(now, deadline);
        if (seconds <= 0) {
            return 60;
        }
        return (int) Math.min(def, seconds);
    }

    private static String messageFor(String state) {
        return switch (state) {
            case STATE_NEED_PASSWORD -> "请输入观看密码";
            case STATE_NEED_LOGIN -> "请先登录";
            case STATE_FORBIDDEN -> "无观看权限";
            case STATE_EXPIRED -> "访问已过期";
            default -> "无观看权限";
        };
    }

    private LocalDateTime now() {
        return LocalDateTime.ofInstant(clock.instant(), SHANGHAI);
    }
}
