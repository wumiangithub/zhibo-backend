package com.zhibo.live.watch;

import com.zhibo.activity.ActivityEntity;
import com.zhibo.common.UuidIds;
import com.zhibo.live.callback.VhallPresenceCallbackService;
import com.zhibo.live.callback.VhallPresenceCallbackService.HandleResult;
import com.zhibo.live.watch.dto.DevPresenceRequest;
import com.zhibo.live.watch.dto.DevPresenceResponse;
import com.zhibo.vhall.config.VhallProperties;
import com.zhibo.vhall.util.VhallCallbackSignUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 本地开发收不到微吼公网回调时，把观看页按钮收成与生产相同的回调体，
 * 签名后交给 {@link VhallPresenceCallbackService}。
 */
@Service
@RequiredArgsConstructor
public class DevPresenceService {

    private final WatchService watchService;
    private final VhallProperties vhallProperties;
    private final VhallPresenceCallbackService callbackService;

    public DevPresenceResponse simulate(String activityId, DevPresenceRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("请求体不能为空");
        }
        String type = request.getType();
        if (!"Join".equals(type) && !"Leave".equals(type)) {
            throw new IllegalArgumentException("type 只能是 Join 或 Leave");
        }
        String email = request.getEmail() == null ? "" : request.getEmail().trim();
        if (email.isEmpty() || email.length() > 64) {
            throw new IllegalArgumentException("email 不能为空且不超过64个字");
        }
        String socketId = request.getSocketId() == null ? "" : request.getSocketId().trim();
        if (socketId.isEmpty() || socketId.length() > 64) {
            throw new IllegalArgumentException("socketId 不能为空且不超过64个字");
        }

        ActivityEntity activity = watchService.requireActivity(activityId);
        if (!"vhall".equals(activity.getProvider())) {
            throw new IllegalArgumentException("仅微吼活动可模拟上下线回调");
        }
        String webinarId = activity.getProviderRoomId() == null ? "" : activity.getProviderRoomId().trim();
        if (webinarId.isEmpty()) {
            throw new IllegalArgumentException("活动还没有微吼房间号，无法模拟回调");
        }
        String secret = vhallProperties.resolveCallbackSecret();
        if (secret == null || secret.isBlank()) {
            throw new IllegalArgumentException("未配置微吼回调密钥，无法模拟回调");
        }

        String nickname = request.getNickname() == null ? "" : request.getNickname().trim();
        if (nickname.length() > 128) {
            nickname = nickname.substring(0, 128);
        }

        String mid = "dev" + UuidIds.newId();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("method_id", VhallPresenceCallbackService.METHOD_PRESENCE);
        body.put("type", type);
        body.put("webinar_id", webinarId);
        body.put("email", email);
        body.put("socket_id", socketId);
        body.put("time", Instant.now().getEpochSecond());
        body.put("mid", mid);
        if (!nickname.isEmpty()) {
            Map<String, Object> context = new LinkedHashMap<>();
            context.put("nickname", nickname);
            body.put("context", context);
        }
        body.put("signature", VhallCallbackSignUtil.sign(secret, body));

        HandleResult result = callbackService.handle(body);
        if (result == HandleResult.OK) {
            return new DevPresenceResponse(mid);
        }
        if (result == HandleResult.BAD_SIGNATURE) {
            throw new IllegalArgumentException("回调校签失败，请检查微吼回调密钥");
        }
        throw new IllegalArgumentException("模拟回调未被接受");
    }
}
