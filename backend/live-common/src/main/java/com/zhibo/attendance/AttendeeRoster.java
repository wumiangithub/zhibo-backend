package com.zhibo.attendance;

import com.zhibo.activity.ActivityEntity;
import com.zhibo.activity.ActivityRepository;
import com.zhibo.activity.AttendanceTimes;
import com.zhibo.activity.RoomAttendanceEntity;
import com.zhibo.activity.RoomAttendanceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.zhibo.common.UuidIds;

/**
 * 中控台名单。admin 旧路径和 live {@code /api/v1/watch/attendees/{id}} 共用。
 * <p>
 * 在线以本站未闭合连接为准：{@code leave_time} 为空即在线。结束 / 点播 / 回放全离线，不改库、不问服务商。
 */
@Service
@RequiredArgsConstructor
public class AttendeeRoster {

    private final ActivityRepository activityRepository;
    private final RoomAttendanceRepository attendanceRepository;

    @Transactional(readOnly = true)
    public AttendeePage list(String activityId, String tab, int page, int pageSize, String keyword) {
        if (page < 1) {
            throw new IllegalArgumentException("page 不能小于 1");
        }
        if (pageSize < 1 || pageSize > 100) {
            throw new IllegalArgumentException("pageSize 需在 1~100");
        }
        String normalizedTab = normalizeTab(tab);
        ActivityEntity activity = requireActivity(activityId);
        boolean forceOffline = isTerminal(activity.getState());
        String needle = keyword == null ? "" : keyword.trim();

        Map<String, RoomAttendanceEntity> byUser = new LinkedHashMap<>();
        for (RoomAttendanceEntity row : attendanceRepository.findByRoomId(activity.getId())) {
            RoomAttendanceEntity current = byUser.get(row.getUserId());
            if (current == null || isLaterVisit(row, current)) {
                byUser.put(row.getUserId(), row);
            }
        }

        List<RoomAttendanceEntity> matched = new ArrayList<>();
        Map<String, Boolean> onlineByUser = new HashMap<>();
        for (RoomAttendanceEntity row : byUser.values()) {
            if (!needle.isEmpty() && (row.getNickname() == null || !row.getNickname().contains(needle))) {
                continue;
            }
            boolean online = resolveOnline(row, forceOffline);
            onlineByUser.put(row.getUserId(), online);
            if ("online".equals(normalizedTab) && !online) {
                continue;
            }
            if ("offline".equals(normalizedTab) && online) {
                continue;
            }
            matched.add(row);
        }
        matched.sort(Comparator.comparing(RoomAttendanceEntity::getEnterTime).reversed());

        int from = (int) Math.min((long) (page - 1) * pageSize, matched.size());
        int to = (int) Math.min(from + (long) pageSize, matched.size());
        List<AttendeeRow> pageItems = new ArrayList<>();
        for (RoomAttendanceEntity row : matched.subList(from, to)) {
            pageItems.add(toRow(row, onlineByUser.getOrDefault(row.getUserId(), false)));
        }
        return new AttendeePage(activity.getState(), matched.size(), page, pageSize, pageItems);
    }

    private static boolean resolveOnline(RoomAttendanceEntity row, boolean forceOffline) {
        return !forceOffline && row.getLeaveTime() == null;
    }

    private static boolean isTerminal(Integer state) {
        return state != null && (state == 3 || state == 4 || state == 5);
    }

    private static boolean isLaterVisit(RoomAttendanceEntity candidate, RoomAttendanceEntity current) {
        int byEnter = candidate.getEnterTime().compareTo(current.getEnterTime());
        if (byEnter != 0) {
            return byEnter > 0;
        }
        if (candidate.getCreatedTime() == null) {
            return false;
        }
        if (current.getCreatedTime() == null) {
            return true;
        }
        return candidate.getCreatedTime().isAfter(current.getCreatedTime());
    }

    private ActivityEntity requireActivity(String id) {
        String uuid = UuidIds.require(id, "活动 id 无效", "活动 id 须为本站 UUID");
        return activityRepository.findById(uuid)
                .orElseThrow(() -> new IllegalArgumentException("活动不存在"));
    }

    private static String normalizeTab(String tab) {
        if (tab == null || tab.isBlank()) {
            return "all";
        }
        String value = tab.trim();
        if (!"all".equals(value) && !"online".equals(value) && !"offline".equals(value)) {
            throw new IllegalArgumentException("tab 无效");
        }
        return value;
    }

    private static AttendeeRow toRow(RoomAttendanceEntity row, boolean online) {
        return new AttendeeRow(
                row.getUserId(),
                emptyToNull(row.getNickname()),
                emptyToNull(row.getAvatarUrl()),
                AttendanceTimes.format(row.getEnterTime()),
                row.getLeaveTime() == null ? null : AttendanceTimes.format(row.getLeaveTime()),
                online);
    }

    private static String emptyToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value;
    }
}
