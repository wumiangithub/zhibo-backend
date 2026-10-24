package com.zhibo.admin.activity;

import com.zhibo.activity.ActivityEntity;
import com.zhibo.activity.ActivityMemberRole;
import com.zhibo.activity.ActivityRepository;
import com.zhibo.admin.activity.dto.ActivityPasscodeResponse;
import com.zhibo.admin.activity.dto.PasscodeEnterRequest;
import com.zhibo.admin.activity.dto.PasscodeEnterResponse;
import com.zhibo.liveprovider.LiveProviderRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;

/**
 * 口令开播：一场一码入库；管理端查/重置；公开 enter 凭口令换主播 hostUrl（H5 与以后 App 共用）。
 */
@Service
@RequiredArgsConstructor
public class HostPasscodeService {

    static final int PASSCODE_LEN = 9;
    static final String DEFAULT_NICKNAME = "讲师";
    private static final int NICKNAME_MAX = 30;
    private static final int ALLOCATE_ATTEMPTS = 32;
    private static final String PASSCODE_EMAIL_PREFIX = "pc";
    private static final String PASSCODE_EMAIL_DOMAIN = "@qkzb.com";

    private final ActivityRepository activityRepository;
    private final LiveProviderRegistry providerRegistry;
    private final SecureRandom secureRandom = new SecureRandom();

    /** 管理端：查本场口令；旧数据无码则补发。 */
    @Transactional
    public ActivityPasscodeResponse getPasscode(String activityId) {
        ActivityEntity entity = requireActivity(activityId);
        assertPasscodeSupported(entity);
        ensurePasscode(entity);
        return toPasscodeResponse(entity.getHostPasscode());
    }

    /** 管理端：重置口令；旧码立刻失效。不结束直播。 */
    @Transactional
    public ActivityPasscodeResponse resetPasscode(String activityId) {
        ActivityEntity entity = requireActivity(activityId);
        assertPasscodeSupported(entity);
        entity.setHostPasscode(allocateUniquePasscode());
        activityRepository.save(entity);
        return toPasscodeResponse(entity.getHostPasscode());
    }

    /**
     * 创建活动时写入唯一口令。碰撞极罕见时重试；仍失败则留给首次 get 再补。
     */
    public void assignOnCreate(ActivityEntity entity) {
        if (entity.getHostPasscode() != null && !entity.getHostPasscode().isBlank()) {
            return;
        }
        entity.setHostPasscode(allocateUniquePasscode());
    }

    /**
     * 公开：凭口令进房。不鉴权、不写成员表；微吼身份用 {@code pc{passcode}@qkzb.com}。
     */
    @Transactional(readOnly = true)
    public PasscodeEnterResponse enter(PasscodeEnterRequest request) {
        String passcode = normalizePasscode(request.getPasscode());
        if (passcode == null) {
            throw new IllegalArgumentException("开播口令无效");
        }
        ActivityEntity entity = activityRepository
                .findByHostPasscodeAndDeletedTimeIsNull(passcode)
                .orElseThrow(() -> new IllegalArgumentException("开播口令无效"));
        assertPasscodeSupported(entity);
        if (!BroadcastRules.canBroadcast(
                entity.getReady(), entity.getRiskStatus(), entity.getShelfStatus(), entity.getState())) {
            throw new IllegalArgumentException("当前状态不可开播");
        }
        String roomId = entity.getProviderRoomId();
        if (roomId == null || roomId.isBlank()) {
            throw new IllegalArgumentException("当前状态不可开播");
        }

        String nickname = resolveNickname(request.getNickname());
        String email = PASSCODE_EMAIL_PREFIX + passcode + PASSCODE_EMAIL_DOMAIN;
        String hostUrl = providerRegistry.require(entity.getProvider())
                .buildRoleJoinUrl(
                        roomId,
                        ActivityMemberRole.HOST.getCode(),
                        email,
                        nickname,
                        null);

        PasscodeEnterResponse response = new PasscodeEnterResponse();
        response.setActivityId(entity.getId());
        response.setProvider(entity.getProvider());
        response.setHostUrl(hostUrl);
        response.setRole(ActivityMemberRole.HOST.getCode());
        response.setNickname(nickname);
        return response;
    }

    private void ensurePasscode(ActivityEntity entity) {
        String existing = trimToNull(entity.getHostPasscode());
        if (existing != null) {
            return;
        }
        for (int i = 0; i < ALLOCATE_ATTEMPTS; i++) {
            String code = randomPasscode();
            if (activityRepository.existsByHostPasscode(code)) {
                continue;
            }
            entity.setHostPasscode(code);
            try {
                activityRepository.saveAndFlush(entity);
                return;
            } catch (DataIntegrityViolationException ignored) {
                entity.setHostPasscode(null);
            }
        }
        throw new IllegalStateException("生成开播口令失败，请重试");
    }

    private String allocateUniquePasscode() {
        for (int i = 0; i < ALLOCATE_ATTEMPTS; i++) {
            String code = randomPasscode();
            if (!activityRepository.existsByHostPasscode(code)) {
                return code;
            }
        }
        throw new IllegalStateException("生成开播口令失败，请重试");
    }

    private String randomPasscode() {
        int n = 100_000_000 + secureRandom.nextInt(900_000_000);
        return String.valueOf(n);
    }

    private ActivityEntity requireActivity(String activityId) {
        String uuid = ActivityIds.requireUuid(activityId);
        ActivityEntity entity = activityRepository.findById(uuid)
                .orElseThrow(() -> new IllegalArgumentException("活动不存在"));
        if (entity.getDeletedTime() != null) {
            throw new IllegalArgumentException("直播不存在");
        }
        return entity;
    }

    /** 定时录播不支持主持人参会，口令开播同样拒绝。 */
    private static void assertPasscodeSupported(ActivityEntity entity) {
        if (entity.getType() != null
                && entity.getType() == 4
                && (entity.getIsTiming() == null || Boolean.TRUE.equals(entity.getIsTiming()))) {
            throw new IllegalArgumentException("定时直播不支持口令开播");
        }
    }

    static String normalizePasscode(String raw) {
        if (raw == null) {
            return null;
        }
        String text = raw.trim();
        if (text.length() != PASSCODE_LEN) {
            return null;
        }
        for (int i = 0; i < text.length(); i++) {
            if (!Character.isDigit(text.charAt(i))) {
                return null;
            }
        }
        return text;
    }

    private static String resolveNickname(String raw) {
        String nick = trimToNull(raw);
        if (nick == null) {
            return DEFAULT_NICKNAME;
        }
        if (nick.length() > NICKNAME_MAX) {
            return nick.substring(0, NICKNAME_MAX);
        }
        return nick;
    }

    private static String trimToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private static ActivityPasscodeResponse toPasscodeResponse(String passcode) {
        ActivityPasscodeResponse response = new ActivityPasscodeResponse();
        response.setPasscode(passcode);
        return response;
    }
}
