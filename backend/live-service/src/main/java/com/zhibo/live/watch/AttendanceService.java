package com.zhibo.live.watch;

import com.zhibo.activity.ActivityEntity;
import com.zhibo.activity.ActivityRepository;
import com.zhibo.activity.AttendanceTimes;
import com.zhibo.live.watch.dto.EnterAttendanceRequest;
import com.zhibo.live.watch.dto.EnterAttendanceResponse;
import com.zhibo.live.watch.dto.LeaveAttendanceItem;
import com.zhibo.live.watch.dto.LeaveAttendanceRequest;
import com.zhibo.live.watch.dto.LeaveAttendanceResponse;
import com.zhibo.presence.PresenceEventEntity;
import com.zhibo.presence.PresenceWriteService;
import com.zhibo.presence.PresenceWriteService.LeaveRosterOutcome;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 进出房辅通道：校验请求后交给 {@link PresenceWriteService} 双写名单+流水。
 * 请求可带 {@code mid}（与微吼回调同一套幂等）；不传则仍按时间去重键。
 */
@Service
@RequiredArgsConstructor
public class AttendanceService {

    static final int LEAVE_BATCH_MAX = 100;
    static final int MID_MAX = 64;

    private final ActivityRepository activityRepository;
    private final PresenceWriteService presenceWriteService;

    @Transactional
    public EnterAttendanceResponse enter(String activityId, EnterAttendanceRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("请求体不能为空");
        }
        ActivityEntity activity = requireActivity(activityId);
        String userId = requireUserId(request.getUserId());
        LocalDateTime enterTime = AttendanceTimes.parse(request.getEnterTime(), "enterTime");
        String nickname = optionalText(request.getNickname(), 128, "nickname");
        String avatarUrl = optionalText(request.getAvatarUrl(), 1024, "avatarUrl");
        String mid = optionalMid(request.getMid(), "mid");

        PresenceWriteService.JoinResult result = presenceWriteService.applyJoin(
                activity.getId(),
                userId,
                enterTime,
                nickname,
                avatarUrl,
                mid,
                PresenceEventEntity.SOURCE_CLIENT);
        if (result.attendanceId() != null) {
            return new EnterAttendanceResponse(result.attendanceId());
        }
        if (result.midDuplicate()) {
            // 同 mid 已处理过，名单行暂不可见时仍幂等成功（极少见）
            return new EnterAttendanceResponse("");
        }
        throw new IllegalStateException("进入记录写入失败");
    }

    /**
     * 先校验整包，通过后才逐条写。缺行 / enterTime 对不上当前会话计入 missing，不整包 400。
     * 同 mid 整次幂等计入 skipped。missing / stale 仍插 Leave 事件；名单 skipped 不插。
     */
    @Transactional
    public LeaveAttendanceResponse leave(String activityId, LeaveAttendanceRequest request) {
        List<ParsedLeave> items = parseLeaveItems(request);
        ActivityEntity activity = requireActivity(activityId);
        int updated = 0;
        int skipped = 0;
        int missing = 0;
        for (ParsedLeave item : items) {
            PresenceWriteService.LeaveResult result = presenceWriteService.applyLeave(
                    activity.getId(),
                    item.userId(),
                    item.leaveTime(),
                    item.enterTime(),
                    item.mid(),
                    PresenceEventEntity.SOURCE_CLIENT);
            if (result.midDuplicate()) {
                skipped++;
                continue;
            }
            LeaveRosterOutcome outcome = result.roster();
            if (outcome == LeaveRosterOutcome.UPDATED) {
                updated++;
            } else if (outcome == LeaveRosterOutcome.SKIPPED) {
                skipped++;
            } else {
                // MISSING / STALE → 契约对外都算 missing
                missing++;
            }
        }
        return new LeaveAttendanceResponse(updated, skipped, missing);
    }

    private static List<ParsedLeave> parseLeaveItems(LeaveAttendanceRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("请求体不能为空");
        }
        List<LeaveAttendanceItem> items = request.getItems();
        if (items == null || items.isEmpty()) {
            throw new IllegalArgumentException("items 不能为空");
        }
        if (items.size() > LEAVE_BATCH_MAX) {
            throw new IllegalArgumentException("items 不能超过100条");
        }
        List<ParsedLeave> parsed = new ArrayList<>(items.size());
        for (int i = 0; i < items.size(); i++) {
            parsed.add(parseLeaveItem(items.get(i), i));
        }
        return parsed;
    }

    private static ParsedLeave parseLeaveItem(LeaveAttendanceItem item, int index) {
        if (item == null) {
            throw new IllegalArgumentException("items[" + index + "] 不能为空");
        }
        String field = "items[" + index + "].";
        return new ParsedLeave(
                requireUserId(item.getUserId(), field + "userId"),
                AttendanceTimes.parse(item.getEnterTime(), field + "enterTime"),
                AttendanceTimes.parse(item.getLeaveTime(), field + "leaveTime"),
                optionalMid(item.getMid(), field + "mid"));
    }

    private ActivityEntity requireActivity(String id) {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("活动 id 无效");
        }
        String uuid;
        try {
            uuid = UUID.fromString(id.trim()).toString();
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("活动 id 须为本站 UUID");
        }
        return activityRepository.findById(uuid)
                .orElseThrow(() -> new IllegalArgumentException("活动不存在"));
    }

    private static String requireUserId(String userId) {
        return requireUserId(userId, "userId");
    }

    private static String requireUserId(String userId, String field) {
        if (userId == null || userId.isBlank()) {
            throw new IllegalArgumentException(field + " 不能为空");
        }
        String value = userId.trim();
        if (value.length() > 128) {
            throw new IllegalArgumentException(field + " 不能超过128个字");
        }
        return value;
    }

    private record ParsedLeave(
            String userId, LocalDateTime enterTime, LocalDateTime leaveTime, String mid) {
    }

    private static String optionalText(String raw, int max, String field) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String value = raw.trim();
        if (value.length() > max) {
            throw new IllegalArgumentException(field + " 不能超过" + max + "个字");
        }
        return value;
    }

    private static String optionalMid(String raw, String field) {
        return optionalText(raw, MID_MAX, field);
    }
}
