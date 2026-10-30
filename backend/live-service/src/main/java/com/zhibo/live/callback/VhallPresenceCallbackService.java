package com.zhibo.live.callback;

import com.zhibo.activity.VhallRoomIdResolver;
import com.zhibo.presence.PresenceEventEntity;
import com.zhibo.presence.PresenceUserIds;
import com.zhibo.presence.PresenceWriteService;
import com.zhibo.vhall.config.VhallProperties;
import com.zhibo.vhall.util.VhallCallbackSignUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Map;

/**
 * 微吼上下线回调（method_id=20）→ 双写。
 * 本地开发模拟也走本类，保证校签与落库和微吼投递相同。
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class VhallPresenceCallbackService {

    public static final int METHOD_PRESENCE = 20;
    static final ZoneId SHANGHAI = ZoneId.of("Asia/Shanghai");

    private final VhallProperties vhallProperties;
    private final VhallRoomIdResolver roomIdResolver;
    private final PresenceWriteService presenceWriteService;

    public enum HandleResult {
        /** 已处理或幂等跳过，微吼应收到 success */
        OK,
        /** 校签失败 */
        BAD_SIGNATURE,
        /** 字段不合法（缺 mid / type 等） */
        BAD_REQUEST,
        /** method_id 不是 20，忽略 */
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
            log.warn("微吼上下线回调校签失败");
            return HandleResult.BAD_SIGNATURE;
        }

        Integer methodId = intVal(body.get("method_id"));
        if (methodId == null || methodId != METHOD_PRESENCE) {
            log.info("微吼回调 method_id={} 非上下线，忽略", methodId);
            return HandleResult.IGNORED;
        }

        String type = stringVal(body.get("type"));
        if (!PresenceEventEntity.TYPE_JOIN.equals(type) && !PresenceEventEntity.TYPE_LEAVE.equals(type)) {
            return HandleResult.BAD_REQUEST;
        }

        String mid = stringVal(body.get("mid"));
        if (mid == null) {
            return HandleResult.BAD_REQUEST;
        }

        String email = stringVal(body.get("email"));
        String userId = PresenceUserIds.parseEmail(email);
        String socketId = stringVal(body.get("socket_id"));
        if (socketId != null && socketId.length() > 64) {
            socketId = socketId.substring(0, 64);
        }

        String webinarId = stringVal(body.get("webinar_id"));
        if (webinarId == null) {
            return HandleResult.BAD_REQUEST;
        }

        Long epochSec = longVal(body.get("time"));
        if (epochSec == null) {
            epochSec = longVal(body.get("m_time"));
        }
        if (epochSec == null) {
            return HandleResult.BAD_REQUEST;
        }
        LocalDateTime eventTime = LocalDateTime.ofInstant(Instant.ofEpochSecond(epochSec), SHANGHAI);

        var roomId = roomIdResolver.resolveRoomId(webinarId);
        if (roomId.isEmpty()) {
            log.warn("微吼上下线回调找不到本站活动 webinar_id={}", webinarId);
            // 未知房间仍 success，避免微吼无限重试
            return HandleResult.OK;
        }

        if (PresenceUserIds.isGuestEmail(email) || !PresenceUserIds.mayWriteRoom(userId)) {
            log.info("微吼上下线回调游客/非可写邮箱，跳过 room_* mid={} email={}", mid, email);
            return HandleResult.OK;
        }
        if (socketId == null) {
            log.info("微吼上下线回调缺少 socket_id，跳过写库 mid={}", mid);
            return HandleResult.OK;
        }
        if (userId.length() > 128) {
            return HandleResult.BAD_REQUEST;
        }

        String nickname = null;
        String avatarUrl = null;
        Object contextObj = body.get("context");
        if (contextObj instanceof Map<?, ?> context) {
            // 观众 Join 的昵称由 PresenceWriteService 从 viewer 表覆盖；员工仍用回调 context
            nickname = truncate(stringVal(context.get("nickname")), 128);
            avatarUrl = truncate(stringVal(context.get("avatar")), 1024);
        }

        if (PresenceEventEntity.TYPE_JOIN.equals(type)) {
            presenceWriteService.applyJoin(
                    roomId.get(),
                    userId,
                    eventTime,
                    nickname,
                    avatarUrl,
                    mid,
                    PresenceEventEntity.SOURCE_CALLBACK,
                    socketId);
        } else {
            presenceWriteService.applyLeave(
                    roomId.get(),
                    userId,
                    eventTime,
                    null,
                    mid,
                    PresenceEventEntity.SOURCE_CALLBACK,
                    socketId);
        }
        return HandleResult.OK;
    }

    private static String stringVal(Object raw) {
        if (raw == null) {
            return null;
        }
        String value = String.valueOf(raw).trim();
        return value.isEmpty() ? null : value;
    }

    private static Integer intVal(Object raw) {
        if (raw instanceof Number n) {
            return n.intValue();
        }
        String s = stringVal(raw);
        if (s == null) {
            return null;
        }
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private static Long longVal(Object raw) {
        if (raw instanceof Number n) {
            return n.longValue();
        }
        String s = stringVal(raw);
        if (s == null) {
            return null;
        }
        try {
            return Long.parseLong(s);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private static String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max);
    }
}
