package com.zhibo.attendance;

import com.zhibo.activity.ActivityAudienceEntity;
import com.zhibo.activity.ActivityAudienceRepository;
import com.zhibo.activity.ActivityEntity;
import com.zhibo.activity.ActivityMemberEntity;
import com.zhibo.activity.ActivityMemberRepository;
import com.zhibo.activity.ActivityMemberRole;
import com.zhibo.activity.ActivityOrderRepository;
import com.zhibo.activity.ActivityProductClickRepository;
import com.zhibo.activity.ActivityRepository;
import com.zhibo.activity.AttendanceTimes;
import com.zhibo.activity.RoomAttendanceEntity;
import com.zhibo.activity.RoomAttendanceRepository;
import com.zhibo.activity.RoomCommentRepository;
import com.zhibo.common.UuidIds;
import com.zhibo.viewer.ViewerEntity;
import com.zhibo.viewer.ViewerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 中控台名单。admin 旧路径和 live {@code /api/v1/watch/attendees/{id}} 共用。
 * <p>
 * 在线以本站未闭合连接为准：{@code leave_time} 为空即在线。结束 / 点播 / 回放全离线，不改库、不问服务商。
 * 学员 / 助教：观众 username；员工角色 {@code e{id}}、中控台 {@code z{id}} 归助教。
 */
@Service
@RequiredArgsConstructor
public class AttendeeRoster {

    private static final List<Integer> PAID_ORDER_STATUSES = List.of(2, 3, 4, 5);

    private final ActivityRepository activityRepository;
    private final RoomAttendanceRepository attendanceRepository;
    private final ViewerRepository viewerRepository;
    private final ActivityMemberRepository memberRepository;
    private final ActivityAudienceRepository audienceRepository;
    private final ActivityProductClickRepository productClickRepository;
    private final ActivityOrderRepository orderRepository;
    private final RoomCommentRepository commentRepository;

    /** 兼容旧调用：关键字按昵称模糊；名单默认学员。 */
    @Transactional(readOnly = true)
    public AttendeePage list(String activityId, String tab, int page, int pageSize, String keyword) {
        return list(activityId, tab, page, pageSize, keyword, "nickname", "student", "all", "all");
    }

    @Transactional(readOnly = true)
    public AttendeePage list(
            String activityId,
            String tab,
            int page,
            int pageSize,
            String keyword,
            String keywordType,
            String kind) {
        return list(activityId, tab, page, pageSize, keyword, keywordType, kind, "all", "all");
    }

    /**
     * @param followerFilter {@code all} / {@code none}（无归属）/ 员工 id 字符串
     * @param behavior {@code all} / {@code comment} / {@code coupon} / {@code productClick} /
     *     {@code pendingPay} / {@code purchase}
     */
    @Transactional(readOnly = true)
    public AttendeePage list(
            String activityId,
            String tab,
            int page,
            int pageSize,
            String keyword,
            String keywordType,
            String kind,
            String followerFilter,
            String behavior) {
        if (page < 1) {
            throw new IllegalArgumentException("page 不能小于 1");
        }
        if (pageSize < 1 || pageSize > 100) {
            throw new IllegalArgumentException("pageSize 需在 1~100");
        }
        String normalizedTab = normalizeTab(tab);
        String type = normalizeKeywordType(keywordType);
        String normalizedKind = normalizeKind(kind);
        FollowerFilter follower = normalizeFollowerFilter(followerFilter);
        String normalizedBehavior = normalizeBehavior(behavior);
        ActivityEntity activity = requireActivity(activityId);
        boolean forceOffline = isTerminal(activity.getState());
        String needle = keyword == null ? "" : keyword.trim();

        Map<String, RoomAttendanceEntity> byUser = new LinkedHashMap<>();
        for (RoomAttendanceEntity row : attendanceRepository.findByRoomId(activity.getId())) {
            RoomAttendanceEntity current = byUser.get(row.getUsername());
            if (current == null || isLaterVisit(row, current)) {
                byUser.put(row.getUsername(), row);
            }
        }

        Map<Long, ActivityMemberEntity> membersByUserId = loadMembersByUserId(activity.getId());

        Set<String> phoneMatched = needle.isEmpty() || !"phone".equals(type)
                ? Set.of()
                : phoneMatchedUsernames(byUser.keySet(), needle);

        /*
         * 数据层级（勿颠倒）：
         * 1) 一级 tab：全部 / 在线 / 离线 —— 只按关键字 + 在线态分桶
         * 2) 二级 kind：学员 / 助教 —— 当前一级 tab 的子集
         * 3) 跟进人 / 行为 —— 只收窄学员支，不影响一级角标、不影响助教
         */
        List<RoomAttendanceEntity> afterKeyword = new ArrayList<>();
        Map<String, Boolean> onlineByUser = new HashMap<>();
        for (RoomAttendanceEntity row : byUser.values()) {
            if (!needle.isEmpty() && !matchesKeyword(row, type, needle, phoneMatched)) {
                continue;
            }
            boolean online = resolveOnline(row, forceOffline);
            onlineByUser.put(row.getUsername(), online);
            afterKeyword.add(row);
        }

        long allTotal = afterKeyword.size();
        long onlineTotal = 0;
        long offlineTotal = 0;
        for (RoomAttendanceEntity row : afterKeyword) {
            if (Boolean.TRUE.equals(onlineByUser.get(row.getUsername()))) {
                onlineTotal++;
            } else {
                offlineTotal++;
            }
        }

        List<RoomAttendanceEntity> tabPool = new ArrayList<>();
        for (RoomAttendanceEntity row : afterKeyword) {
            boolean online = Boolean.TRUE.equals(onlineByUser.get(row.getUsername()));
            if ("online".equals(normalizedTab) && !online) {
                continue;
            }
            if ("offline".equals(normalizedTab) && online) {
                continue;
            }
            tabPool.add(row);
        }

        Map<String, Long> inviterByUser = loadInviterByUsername(activity.getId(), tabPool);
        Set<String> behaviorHit = resolveBehaviorUsernames(
                activity.getId(), tabPool, normalizedBehavior);

        long studentTotal = 0;
        long assistantTotal = 0;
        List<RoomAttendanceEntity> matched = new ArrayList<>();
        for (RoomAttendanceEntity row : tabPool) {
            boolean assistant = isAssistantUsername(row.getUsername());
            if (assistant) {
                assistantTotal++;
                if ("assistant".equals(normalizedKind)) {
                    matched.add(row);
                }
                continue;
            }
            if (!matchesFollower(row.getUsername(), inviterByUser, follower)) {
                continue;
            }
            if (!"all".equals(normalizedBehavior) && !behaviorHit.contains(row.getUsername())) {
                continue;
            }
            studentTotal++;
            if ("student".equals(normalizedKind)) {
                matched.add(row);
            }
        }
        matched.sort(Comparator.comparing(RoomAttendanceEntity::getEnterTime).reversed());

        int from = (int) Math.min((long) (page - 1) * pageSize, matched.size());
        int to = (int) Math.min(from + (long) pageSize, matched.size());
        List<AttendeeRow> pageItems = new ArrayList<>();
        for (RoomAttendanceEntity row : matched.subList(from, to)) {
            pageItems.add(toRow(
                    row,
                    onlineByUser.getOrDefault(row.getUsername(), false),
                    resolveRoleLabel(row.getUsername(), membersByUserId)));
        }
        return new AttendeePage(
                activity.getState(),
                matched.size(),
                studentTotal,
                assistantTotal,
                allTotal,
                onlineTotal,
                offlineTotal,
                page,
                pageSize,
                pageItems);
    }

    private Map<Long, ActivityMemberEntity> loadMembersByUserId(String activityId) {
        Map<Long, ActivityMemberEntity> map = new HashMap<>();
        for (ActivityMemberEntity member : memberRepository.findByActivityIds(List.of(activityId))) {
            if (member.getId() != null && member.getId().getUserId() != null) {
                map.put(member.getId().getUserId(), member);
            }
        }
        return map;
    }

    private Map<String, Long> loadInviterByUsername(
            String activityId, List<RoomAttendanceEntity> rows) {
        Set<String> usernames = new HashSet<>();
        for (RoomAttendanceEntity row : rows) {
            if (row.getUsername() != null && !isAssistantUsername(row.getUsername())) {
                usernames.add(row.getUsername());
            }
        }
        Map<String, Long> map = new HashMap<>();
        if (usernames.isEmpty()) {
            return map;
        }
        for (ActivityAudienceEntity audience :
                audienceRepository.findByActivityIdAndUsernameIn(activityId, usernames)) {
            if (audience.getUsername() == null) {
                continue;
            }
            Long inviter = audience.getEmployeeInviterId();
            if (inviter != null && inviter > 0) {
                map.put(audience.getUsername(), inviter);
            }
        }
        return map;
    }

    private Set<String> resolveBehaviorUsernames(
            String activityId, List<RoomAttendanceEntity> rows, String behavior) {
        if ("all".equals(behavior)) {
            return Set.of();
        }
        Set<String> usernames = new HashSet<>();
        for (RoomAttendanceEntity row : rows) {
            if (row.getUsername() != null && !isAssistantUsername(row.getUsername())) {
                usernames.add(row.getUsername());
            }
        }
        if (usernames.isEmpty()) {
            return Set.of();
        }
        return switch (behavior) {
            case "comment" -> usernamesWithPositiveCount(
                    commentRepository.countActiveByUsernames(activityId, usernames));
            case "coupon" -> Set.of(); // 本站无领券流水，筛此项恒空
            case "productClick" -> usernamesWithPositiveCount(
                    productClickRepository.countByActivityAndUsernames(activityId, usernames));
            case "pendingPay" -> usernamesWithOrderStatus(activityId, usernames, Set.of(1));
            case "purchase" -> usernamesWithOrderStatus(
                    activityId, usernames, new HashSet<>(PAID_ORDER_STATUSES));
            default -> Set.of();
        };
    }

    private Set<String> usernamesWithOrderStatus(
            String activityId, Collection<String> usernames, Set<Integer> statuses) {
        Set<String> hit = new HashSet<>();
        for (Object[] row : orderRepository.countByActivityUsernameAndStatus(activityId, usernames)) {
            String username = (String) row[0];
            Integer status = (Integer) row[1];
            long count = ((Number) row[2]).longValue();
            if (username != null && status != null && statuses.contains(status) && count > 0) {
                hit.add(username);
            }
        }
        return hit;
    }

    private static Set<String> usernamesWithPositiveCount(List<Object[]> rows) {
        Set<String> hit = new HashSet<>();
        for (Object[] row : rows) {
            if (row[0] == null) {
                continue;
            }
            if (((Number) row[1]).longValue() > 0) {
                hit.add((String) row[0]);
            }
        }
        return hit;
    }

    private static boolean matchesFollower(
            String username, Map<String, Long> inviterByUser, FollowerFilter filter) {
        if (filter.mode() == FollowerMode.ALL) {
            return true;
        }
        Long inviter = inviterByUser.get(username);
        if (filter.mode() == FollowerMode.NONE) {
            return inviter == null;
        }
        return inviter != null && inviter.equals(filter.employeeId());
    }

    private record FollowerFilter(FollowerMode mode, Long employeeId) {}

    private enum FollowerMode {
        ALL,
        NONE,
        EMPLOYEE
    }

    private static FollowerFilter normalizeFollowerFilter(String raw) {
        if (raw == null || raw.isBlank() || "all".equalsIgnoreCase(raw.trim())) {
            return new FollowerFilter(FollowerMode.ALL, null);
        }
        String value = raw.trim();
        if ("none".equalsIgnoreCase(value)) {
            return new FollowerFilter(FollowerMode.NONE, null);
        }
        try {
            long id = Long.parseLong(value);
            if (id <= 0) {
                throw new IllegalArgumentException("followerFilter 员工 id 无效");
            }
            return new FollowerFilter(FollowerMode.EMPLOYEE, id);
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("followerFilter 仅支持 all / none / 员工id");
        }
    }

    private static String normalizeBehavior(String behavior) {
        if (behavior == null || behavior.isBlank()) {
            return "all";
        }
        String value = behavior.trim();
        if ("all".equals(value)
                || "comment".equals(value)
                || "coupon".equals(value)
                || "productClick".equals(value)
                || "pendingPay".equals(value)
                || "purchase".equals(value)) {
            return value;
        }
        throw new IllegalArgumentException(
                "behavior 仅支持 all / comment / coupon / productClick / pendingPay / purchase");
    }

    private Set<String> phoneMatchedUsernames(Set<String> usernames, String needle) {
        if (usernames.isEmpty()) {
            return Set.of();
        }
        Set<String> hit = new HashSet<>();
        for (ViewerEntity viewer : viewerRepository.findByUsernameIn(usernames)) {
            String phone = viewer.getPhone();
            if (phone != null && phone.contains(needle) && viewer.getUsername() != null) {
                hit.add(viewer.getUsername());
            }
        }
        return hit;
    }

    private static boolean matchesKeyword(
            RoomAttendanceEntity row,
            String type,
            String needle,
            Set<String> phoneMatched) {
        return switch (type) {
            case "username" -> row.getUsername() != null && row.getUsername().equals(needle);
            case "phone" -> phoneMatched.contains(row.getUsername());
            default -> row.getNickname() != null && row.getNickname().contains(needle);
        };
    }

    /** 员工角色 e{id}、中控台 z{id} → 助教侧；其余为学员。 */
    static boolean isAssistantUsername(String username) {
        if (username == null || username.length() < 2) {
            return false;
        }
        char prefix = username.charAt(0);
        if (prefix != 'e' && prefix != 'z') {
            return false;
        }
        for (int i = 1; i < username.length(); i++) {
            if (!Character.isDigit(username.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    private static String resolveRoleLabel(String username, Map<Long, ActivityMemberEntity> membersByUserId) {
        if (username == null || username.length() < 2) {
            return null;
        }
        char prefix = username.charAt(0);
        if (prefix == 'z') {
            return "中控";
        }
        if (prefix != 'e') {
            return null;
        }
        long userId;
        try {
            userId = Long.parseLong(username.substring(1));
        } catch (NumberFormatException ex) {
            return "助教";
        }
        ActivityMemberEntity member = membersByUserId.get(userId);
        if (member == null) {
            return "助教";
        }
        if (member.getTag() != null && !member.getTag().isBlank()) {
            return member.getTag().trim();
        }
        try {
            return ActivityMemberRole.requireCode(member.getRole()).getDefaultTag();
        } catch (IllegalArgumentException ex) {
            return "助教";
        }
    }

    private static String normalizeKeywordType(String keywordType) {
        if (keywordType == null || keywordType.isBlank()) {
            return "nickname";
        }
        String value = keywordType.trim().toLowerCase(Locale.ROOT);
        if (!"nickname".equals(value) && !"username".equals(value) && !"phone".equals(value)) {
            throw new IllegalArgumentException("keywordType 仅支持 nickname / username / phone");
        }
        return value;
    }

    private static String normalizeKind(String kind) {
        if (kind == null || kind.isBlank()) {
            return "student";
        }
        String value = kind.trim().toLowerCase(Locale.ROOT);
        if (!"student".equals(value) && !"assistant".equals(value)) {
            throw new IllegalArgumentException("kind 仅支持 student / assistant");
        }
        return value;
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

    private static AttendeeRow toRow(RoomAttendanceEntity row, boolean online, String roleLabel) {
        return new AttendeeRow(
                row.getUsername(),
                emptyToNull(row.getNickname()),
                emptyToNull(row.getAvatarUrl()),
                AttendanceTimes.format(row.getEnterTime()),
                row.getLeaveTime() == null ? null : AttendanceTimes.format(row.getLeaveTime()),
                online,
                emptyToNull(roleLabel));
    }

    private static String emptyToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value;
    }
}
