package com.zhibo.admin.presence;

import com.zhibo.activity.ActivityEntity;
import com.zhibo.admin.activity.ActivityService;
import com.zhibo.admin.presence.dto.DevPresenceItemResult;
import com.zhibo.admin.presence.dto.DevPresenceRequest;
import com.zhibo.admin.presence.dto.DevPresenceResponse;
import com.zhibo.common.UuidIds;
import com.zhibo.presence.PresenceEventEntity;
import com.zhibo.presence.PresenceUserIds;
import com.zhibo.presence.PresenceWriteService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 管理端本地模拟上下线：直接双写名单+流水，供统计联调灌数。
 * 支持观众 {@code username} 与员工角色 {@code e{id}}；socketId 按用户键稳定生成。
 */
@Service
@RequiredArgsConstructor
public class DevPresenceService {

    private static final ZoneId SHANGHAI = ZoneId.of("Asia/Shanghai");
    /** 稳定前缀，避免与观看页 / 微吼真实 socket 冲突。 */
    private static final String SOCKET_PREFIX = "asim_";

    private final ActivityService activityService;
    private final PresenceWriteService presenceWriteService;

    public DevPresenceResponse simulate(String activityId, DevPresenceRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("请求体不能为空");
        }
        String type = request.getType();
        if (!"Join".equals(type) && !"Leave".equals(type)) {
            throw new IllegalArgumentException("type 只能是 Join 或 Leave");
        }
        List<String> raw = request.getUsernames();
        if (raw == null || raw.isEmpty()) {
            throw new IllegalArgumentException("usernames 不能为空");
        }
        if (raw.size() > 200) {
            throw new IllegalArgumentException("usernames 最多 200 个");
        }

        ActivityEntity activity = activityService.requireActivity(activityId);
        String roomId = activity.getId();
        LocalDateTime eventTime = LocalDateTime.now(SHANGHAI);

        Set<String> seen = new LinkedHashSet<>();
        List<DevPresenceItemResult> items = new ArrayList<>();
        int ok = 0;
        int failed = 0;

        for (String rawName : raw) {
            String username = rawName == null ? "" : rawName.trim();
            if (username.isEmpty()) {
                failed++;
                items.add(new DevPresenceItemResult("", false, null, "username 不能为空"));
                continue;
            }
            if (!seen.add(username)) {
                continue;
            }
            if (!isAllowedPresenceUser(username)) {
                failed++;
                items.add(new DevPresenceItemResult(
                        username, false, null, "仅支持观众 username 或员工 e{id}"));
                continue;
            }

            String mid = "adev" + UuidIds.newId();
            String socketId = socketIdFor(username);
            try {
                if ("Join".equals(type)) {
                    presenceWriteService.applyJoin(
                            roomId,
                            username,
                            eventTime,
                            null,
                            null,
                            mid,
                            PresenceEventEntity.SOURCE_CALLBACK,
                            socketId);
                } else {
                    presenceWriteService.applyLeave(
                            roomId,
                            username,
                            eventTime,
                            null,
                            mid,
                            PresenceEventEntity.SOURCE_CALLBACK,
                            socketId);
                }
                ok++;
                items.add(new DevPresenceItemResult(username, true, mid, null));
            } catch (RuntimeException ex) {
                failed++;
                String msg = ex.getMessage() == null || ex.getMessage().isBlank()
                        ? "写入失败"
                        : ex.getMessage();
                items.add(new DevPresenceItemResult(username, false, null, msg));
            }
        }

        return new DevPresenceResponse(ok, failed, items);
    }

    /** 观众 username，或员工角色 {@code e{id}}（不含中控 {@code z{id}} / 游客）。 */
    static boolean isAllowedPresenceUser(String username) {
        if (PresenceUserIds.viewerUsername(username) != null) {
            return true;
        }
        return PresenceUserIds.isStaff(username) && PresenceUserIds.mayWriteRoom(username);
    }

    /** 同用户反复上/下线共用；≤64。 */
    static String socketIdFor(String username) {
        String id = SOCKET_PREFIX + username.trim();
        return id.length() <= 64 ? id : id.substring(0, 64);
    }
}
