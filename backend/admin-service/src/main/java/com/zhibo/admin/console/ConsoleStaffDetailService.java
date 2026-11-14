package com.zhibo.admin.console;

import com.zhibo.activity.ActivityAudienceEntity;
import com.zhibo.activity.ActivityAudienceRepository;
import com.zhibo.activity.ActivityEntity;
import com.zhibo.activity.ActivityLiveSessionRepository;
import com.zhibo.activity.ActivityRepository;
import com.zhibo.activity.RoomAttendanceEntity;
import com.zhibo.activity.RoomAttendanceRepository;
import com.zhibo.activity.RoomCommentRepository;
import com.zhibo.admin.auth.SysUserAccountEntity;
import com.zhibo.admin.auth.SysUserAccountRepository;
import com.zhibo.admin.console.dto.ConsoleStaffDetailItem;
import com.zhibo.admin.console.dto.ConsoleStaffDetailListResponse;
import com.zhibo.admin.org.EmployeeDeptResolver;
import com.zhibo.common.UuidIds;
import com.zhibo.presence.PresenceEventEntity;
import com.zhibo.presence.PresenceEventRepository;
import com.zhibo.presence.PresenceUserIds;
import com.zhibo.presence.WatchDurationCalculator;
import com.zhibo.replay.RoomReplayWatchRepository;
import java.text.Collator;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 中控台底部员工数据（D125）：按专属链邀请人聚合进房/观看/在线/发言。
 */
@Service
@RequiredArgsConstructor
public class ConsoleStaffDetailService {

    private static final ZoneId SHANGHAI = ZoneId.of("Asia/Shanghai");
    private static final Set<String> SORT_KEYS = Set.of(
            "inviteCount",
            "liveWatchCount",
            "avgLiveWatchDuration",
            "onlineCount",
            "commentCount",
            "employeeName",
            "deptName");

    private final ActivityRepository activityRepository;
    private final ActivityAudienceRepository audienceRepository;
    private final PresenceEventRepository presenceEventRepository;
    private final RoomAttendanceRepository attendanceRepository;
    private final RoomReplayWatchRepository replayWatchRepository;
    private final ActivityLiveSessionRepository liveSessionRepository;
    private final RoomCommentRepository commentRepository;
    private final SysUserAccountRepository userRepository;
    private final EmployeeDeptResolver employeeDeptResolver;
    private final Clock clock;

    @Transactional(readOnly = true)
    public ConsoleStaffDetailListResponse list(
            String activityId,
            int page,
            int pageSize,
            Long employeeId,
            String employeeKeyword,
            Long deptId,
            String sort,
            String order) {
        if (page < 1) {
            throw new IllegalArgumentException("page 不能小于 1");
        }
        if (pageSize < 1 || pageSize > 100) {
            throw new IllegalArgumentException("pageSize 需在 1~100");
        }
        List<ConsoleStaffDetailItem> all =
                prepareRows(activityId, employeeId, employeeKeyword, deptId, sort, order);
        int from = (int) Math.min((long) (page - 1) * pageSize, all.size());
        int to = (int) Math.min(from + (long) pageSize, all.size());
        ConsoleStaffDetailListResponse resp = new ConsoleStaffDetailListResponse();
        resp.setPage(page);
        resp.setPageSize(pageSize);
        resp.setTotal(all.size());
        resp.setList(new ArrayList<>(all.subList(from, to)));
        return resp;
    }

    private List<ConsoleStaffDetailItem> prepareRows(
            String activityId,
            Long employeeId,
            String employeeKeyword,
            Long deptId,
            String sort,
            String order) {
        ActivityEntity activity = requireActivity(activityId);
        String uuid = activity.getId();
        Long employeeFilterId = normalizeEmployeeId(employeeId);
        String employeeKw = employeeKeyword == null ? "" : employeeKeyword.trim();
        Long deptFilterId = normalizeDeptId(deptId);
        String sortKey = normalizeSort(sort);
        boolean asc = "asc".equalsIgnoreCase(order == null ? "desc" : order.trim());
        if (order != null && !order.isBlank()) {
            String o = order.trim().toLowerCase(Locale.ROOT);
            if (!"asc".equals(o) && !"desc".equals(o)) {
                throw new IllegalArgumentException("order 须为 asc 或 desc");
            }
        }

        List<ActivityAudienceEntity> audiences = audienceRepository.findByActivityId(uuid);
        Map<Long, Set<String>> inviteesByEmployee = new LinkedHashMap<>();
        for (ActivityAudienceEntity a : audiences) {
            Long inviter = a.getEmployeeInviterId();
            if (inviter == null || inviter <= 0 || a.getUsername() == null || a.getUsername().isBlank()) {
                continue;
            }
            inviteesByEmployee
                    .computeIfAbsent(inviter, k -> new HashSet<>())
                    .add(a.getUsername().trim());
        }
        if (inviteesByEmployee.isEmpty()) {
            return List.of();
        }

        Set<String> allInviteeUsernames = new HashSet<>();
        for (Set<String> set : inviteesByEmployee.values()) {
            allInviteeUsernames.addAll(set);
        }

        List<PresenceEventEntity> events = presenceEventRepository.findByRoomId(uuid);
        Set<String> joinedViewers = loadJoinedViewers(events);
        boolean forceOffline = isTerminal(activity.getState());
        Map<String, RoomAttendanceEntity> attendanceByUser = loadAttendance(uuid);
        Map<String, WatchDurationCalculator.Durations> durations = WatchDurationCalculator.byViewer(
                activity,
                events,
                replayWatchRepository.findByRoomId(uuid),
                liveSessionRepository.findByActivityId(uuid),
                LocalDateTime.ofInstant(clock.instant(), SHANGHAI));
        Map<String, Long> comments = toLongMap(
                commentRepository.countActiveByUsernames(uuid, allInviteeUsernames));

        Set<Long> employeeIds = inviteesByEmployee.keySet();
        Map<Long, String> employeeNames = loadEmployeeNames(employeeIds);
        Map<Long, EmployeeDeptResolver.Dept> depts = employeeDeptResolver.resolvePrimaryBatch(employeeIds);

        List<ConsoleStaffDetailItem> rows = new ArrayList<>();
        for (Map.Entry<Long, Set<String>> e : inviteesByEmployee.entrySet()) {
            Long empId = e.getKey();
            String name = employeeNames.get(empId);
            if (name == null || name.isBlank()) {
                name = "员工" + empId;
            }
            if (employeeFilterId != null && !employeeFilterId.equals(empId)) {
                continue;
            }
            if (!employeeKw.isEmpty() && !name.contains(employeeKw)) {
                continue;
            }
            EmployeeDeptResolver.Dept dept = depts.get(empId);
            if (deptFilterId != null) {
                if (dept == null || dept.deptId() != deptFilterId) {
                    continue;
                }
            }

            int inviteCount = 0;
            int liveWatchCount = 0;
            long totalLiveSec = 0L;
            int onlineCount = 0;
            int commentCount = 0;
            for (String username : e.getValue()) {
                commentCount += comments.getOrDefault(username, 0L).intValue();
                if (!joinedViewers.contains(username)) {
                    continue;
                }
                inviteCount++;
                liveWatchCount++;
                WatchDurationCalculator.Durations d =
                        durations.getOrDefault(username, WatchDurationCalculator.zero());
                totalLiveSec += Math.max(0L, d.liveSec());
                RoomAttendanceEntity attendance = attendanceByUser.get(username);
                boolean online = !forceOffline && attendance != null && attendance.getLeaveTime() == null;
                if (online) {
                    onlineCount++;
                }
            }
            long avgSec = liveWatchCount == 0 ? 0L : totalLiveSec / liveWatchCount;

            ConsoleStaffDetailItem item = new ConsoleStaffDetailItem();
            item.setEmployeeId(empId);
            item.setEmployeeName(name);
            if (dept != null) {
                item.setDeptId(dept.deptId());
                item.setDeptName(dept.deptName());
            }
            item.setInviteCount(inviteCount);
            item.setLiveWatchCount(liveWatchCount);
            item.setAvgLiveWatchDurationSec(avgSec);
            item.setOnlineCount(onlineCount);
            item.setCommentCount(commentCount);
            rows.add(item);
        }

        Comparator<ConsoleStaffDetailItem> cmp = comparator(sortKey);
        if (!asc) {
            cmp = cmp.reversed();
        }
        Collator zh = Collator.getInstance(Locale.CHINA);
        rows.sort(cmp.thenComparing(ConsoleStaffDetailItem::getEmployeeName, zh)
                .thenComparing(ConsoleStaffDetailItem::getEmployeeId));
        return rows;
    }

    private static Set<String> loadJoinedViewers(List<PresenceEventEntity> events) {
        Set<String> joined = new HashSet<>();
        for (PresenceEventEntity event : events) {
            if (!PresenceEventEntity.TYPE_JOIN.equals(event.getType())) {
                continue;
            }
            String username = PresenceUserIds.viewerUsername(event.getUsername());
            if (username != null) {
                joined.add(username);
            }
        }
        return joined;
    }

    private Map<String, RoomAttendanceEntity> loadAttendance(String roomId) {
        Map<String, RoomAttendanceEntity> map = new HashMap<>();
        for (RoomAttendanceEntity row : attendanceRepository.findByRoomId(roomId)) {
            if (row.getUsername() == null || row.getUsername().isBlank()) {
                continue;
            }
            map.put(row.getUsername().trim(), row);
        }
        return map;
    }

    private Map<Long, String> loadEmployeeNames(Set<Long> ids) {
        Map<Long, String> names = new HashMap<>();
        if (ids.isEmpty()) {
            return names;
        }
        for (SysUserAccountEntity u : userRepository.findAllById(ids)) {
            if (u.getUserName() != null && !u.getUserName().isBlank()) {
                names.put(u.getId(), u.getUserName().trim());
            }
        }
        return names;
    }

    private static Map<String, Long> toLongMap(List<Object[]> rows) {
        Map<String, Long> map = new HashMap<>();
        if (rows == null) {
            return map;
        }
        for (Object[] row : rows) {
            if (row == null || row.length < 2 || row[0] == null || row[1] == null) {
                continue;
            }
            map.put(String.valueOf(row[0]), ((Number) row[1]).longValue());
        }
        return map;
    }

    private static Comparator<ConsoleStaffDetailItem> comparator(String sortKey) {
        return switch (sortKey) {
            case "liveWatchCount" -> Comparator.comparingInt(ConsoleStaffDetailItem::getLiveWatchCount);
            case "avgLiveWatchDuration" ->
                    Comparator.comparingLong(ConsoleStaffDetailItem::getAvgLiveWatchDurationSec);
            case "onlineCount" -> Comparator.comparingInt(ConsoleStaffDetailItem::getOnlineCount);
            case "commentCount" -> Comparator.comparingInt(ConsoleStaffDetailItem::getCommentCount);
            case "employeeName" -> {
                Collator zh = Collator.getInstance(Locale.CHINA);
                yield Comparator.comparing(ConsoleStaffDetailItem::getEmployeeName, zh);
            }
            case "deptName" -> {
                Collator zh = Collator.getInstance(Locale.CHINA);
                yield Comparator.comparing(
                        (ConsoleStaffDetailItem i) -> i.getDeptName() == null ? "" : i.getDeptName(), zh);
            }
            default -> Comparator.comparingInt(ConsoleStaffDetailItem::getInviteCount);
        };
    }

    private static String normalizeSort(String sort) {
        if (sort == null || sort.isBlank()) {
            return "inviteCount";
        }
        String key = sort.trim();
        if (!SORT_KEYS.contains(key)) {
            throw new IllegalArgumentException("sort 非法");
        }
        return key;
    }

    private static Long normalizeEmployeeId(Long employeeId) {
        if (employeeId == null) {
            return null;
        }
        if (employeeId <= 0) {
            throw new IllegalArgumentException("employeeId 须为正整数");
        }
        return employeeId;
    }

    private static Long normalizeDeptId(Long deptId) {
        if (deptId == null) {
            return null;
        }
        if (deptId <= 0) {
            throw new IllegalArgumentException("deptId 须为正整数");
        }
        return deptId;
    }

    private static boolean isTerminal(Integer state) {
        return state != null && (state == 3 || state == 4 || state == 5);
    }

    private ActivityEntity requireActivity(String activityId) {
        String uuid = UuidIds.require(activityId, "活动 id 无效", "活动 id 须为本站 UUID");
        return activityRepository
                .findById(uuid)
                .orElseThrow(() -> new IllegalArgumentException("活动不存在"));
    }
}
