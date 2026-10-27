package com.zhibo.live.callback;

import com.zhibo.activity.ActivityEntity;
import com.zhibo.activity.ActivityRepository;
import com.zhibo.vhall.config.VhallProperties;
import com.zhibo.vhall.util.VhallCallbackSignUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.Optional;

/**
 * 微吼活动开始/结束回调（{@code method_id=1}）：
 * {@code type=1} → 本站 {@code state=1}（直播）；{@code type=3} → {@code state=3}（结束）。
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class VhallLiveStateCallbackService {

    /** 活动开始 / 结束 */
    static final int METHOD_LIVE_STATE = 1;
    /** 开始直播 */
    static final int TYPE_START = 1;
    /** 结束直播 */
    static final int TYPE_END = 3;
    /** 本站：直播中 */
    static final int STATE_LIVE = 1;
    /** 本站：已结束 */
    static final int STATE_ENDED = 3;

    private final VhallProperties vhallProperties;
    private final VhallRoomIdResolver roomIdResolver;
    private final ActivityRepository activityRepository;

    public enum HandleResult {
        OK,
        BAD_SIGNATURE,
        BAD_REQUEST,
        IGNORED
    }

    @Transactional
    public HandleResult handle(Map<String, Object> body) {
        if (body == null || body.isEmpty()) {
            return HandleResult.BAD_REQUEST;
        }

        String signature = stringVal(body.get("signature"));
        if (signature == null) {
            signature = stringVal(body.get("sign"));
        }
        String secret = vhallProperties.resolveCallbackSecret();
        if (secret == null || secret.isBlank() || !VhallCallbackSignUtil.matches(secret, body, signature)) {
            log.warn("微吼开播/结束回调校签失败");
            return HandleResult.BAD_SIGNATURE;
        }

        Integer methodId = intVal(body.get("method_id"));
        if (methodId == null) {
            return HandleResult.BAD_REQUEST;
        }
        if (methodId != METHOD_LIVE_STATE) {
            log.info("微吼开播/结束回调 method_id={} 非开始结束，忽略", methodId);
            return HandleResult.IGNORED;
        }

        Integer type = intVal(body.get("type"));
        if (type == null || (type != TYPE_START && type != TYPE_END)) {
            return HandleResult.BAD_REQUEST;
        }

        String webinarId = stringVal(body.get("webinar_id"));
        if (webinarId == null) {
            return HandleResult.BAD_REQUEST;
        }

        Optional<String> roomId = roomIdResolver.resolveRoomId(webinarId);
        if (roomId.isEmpty()) {
            log.warn("微吼开播/结束回调找不到本站活动 webinar_id={}", webinarId);
            // 未知仍 success，避免微吼无限重试
            return HandleResult.OK;
        }

        Optional<ActivityEntity> found = activityRepository.findById(roomId.get());
        if (found.isEmpty()) {
            log.warn("微吼开播/结束回调活动已不存在 id={}", roomId.get());
            return HandleResult.OK;
        }
        ActivityEntity entity = found.get();
        if (entity.getDeletedTime() != null) {
            log.info("微吼开播/结束回调活动已软删 id={} webinar_id={}", entity.getId(), webinarId);
            return HandleResult.OK;
        }

        int newState = type == TYPE_START ? STATE_LIVE : STATE_ENDED;
        Integer oldState = entity.getState();
        if (oldState != null && oldState == newState) {
            return HandleResult.OK;
        }
        entity.setState(newState);
        activityRepository.save(entity);
        log.info("微吼开播/结束回调写回 state id={} webinar_id={} {}→{}",
                entity.getId(), webinarId, oldState, newState);
        return HandleResult.OK;
    }

    private static String stringVal(Object raw) {
        if (raw == null) {
            return null;
        }
        String s = String.valueOf(raw).trim();
        return s.isEmpty() || "null".equalsIgnoreCase(s) ? null : s;
    }

    private static Integer intVal(Object raw) {
        if (raw == null) {
            return null;
        }
        if (raw instanceof Number n) {
            return n.intValue();
        }
        String s = String.valueOf(raw).trim();
        if (s.isEmpty()) {
            return null;
        }
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException ex) {
            return null;
        }
    }
}
