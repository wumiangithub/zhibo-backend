package com.zhibo.admin.console;

import com.zhibo.activity.ActivityAudienceEntity;
import com.zhibo.activity.ActivityAudienceRepository;
import com.zhibo.activity.ActivityEntity;
import com.zhibo.activity.ActivityLiveSessionRepository;
import com.zhibo.activity.ActivityOrderRepository;
import com.zhibo.activity.ActivityProductClickRepository;
import com.zhibo.activity.ActivityRepository;
import com.zhibo.activity.AttendanceTimes;
import com.zhibo.activity.RoomAttendanceEntity;
import com.zhibo.activity.RoomAttendanceRepository;
import com.zhibo.activity.RoomCommentRepository;
import com.zhibo.admin.auth.SysUserAccountEntity;
import com.zhibo.admin.auth.SysUserAccountRepository;
import com.zhibo.admin.console.dto.ConsoleAudienceDetailItem;
import com.zhibo.admin.console.dto.ConsoleAudienceDetailListResponse;
import com.zhibo.admin.viewer.ViewerAdminService;
import com.zhibo.common.UuidIds;
import com.zhibo.presence.PresenceEventEntity;
import com.zhibo.presence.PresenceEventRepository;
import com.zhibo.presence.PresenceUserIds;
import com.zhibo.presence.WatchDurationCalculator;
import com.zhibo.replay.RoomReplayWatchRepository;
import com.zhibo.viewer.ViewerDisplayNames;
import com.zhibo.viewer.ViewerEntity;
import com.zhibo.viewer.ViewerRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 中控台底部观众明细（D123）：实际进房观众 + 多表聚合；不复用 attendees 名单接口。
 */
@Service
@RequiredArgsConstructor
public class ConsoleAudienceDetailService {

    private static final ZoneId SHANGHAI = ZoneId.of("Asia/Shanghai");
    private static final byte[] UTF8_BOM = new byte[] {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};
    private static final Set<String> KEYWORD_TYPES = Set.of("nickname", "username", "phone");
    private static final Set<String> ONLINE_STATUS = Set.of("all", "online", "offline");
    private static final Set<String> SORT_KEYS = Set.of(
            "firstEnterTime",
            "latestEnterTime",
            "liveWatchDuration",
            "replayWatchDuration",
            "totalWatchDuration",
            "leaveTime",
            "enterCount",
            "online",
            "productClickCount",
            "pendingPayCount",
            "purchaseCount",
            "totalPurchaseCount",
            "totalPurchaseAmount",
            "commentCount",
            "couponReceiveCount");
    private static final List<Integer> PAID_STATUSES =
            com.zhibo.activity.ActivityOrderStatuses.PAID_STATUSES;

    private final ActivityRepository activityRepository;
    private final PresenceEventRepository presenceEventRepository;
    private final RoomAttendanceRepository attendanceRepository;
    private final RoomReplayWatchRepository replayWatchRepository;
    private final ActivityLiveSessionRepository liveSessionRepository;
    private final ActivityAudienceRepository audienceRepository;
    private final ActivityProductClickRepository productClickRepository;
    private final ActivityOrderRepository orderRepository;
    private final RoomCommentRepository commentRepository;
    private final ViewerRepository viewerRepository;
    private final SysUserAccountRepository userRepository;
    private final Clock clock;

    @Transactional(readOnly = true)
    public ConsoleAudienceDetailListResponse list(
            String activityId,
            int page,
            int pageSize,
            String keywordType,
            String keyword,
            String onlineStatus,
            Long followerEmployeeId,
            String followerKeyword,
            String sort,
            String order) {
        if (page < 1) {
            throw new IllegalArgumentException("page 不能小于 1");
        }
        if (pageSize < 1 || pageSize > 100) {
            throw new IllegalArgumentException("pageSize 需在 1~100");
        }
        List<ConsoleAudienceDetailItem> all = prepareRows(
                activityId,
                keywordType,
                keyword,
                onlineStatus,
                followerEmployeeId,
                followerKeyword,
                sort,
                order);
        int from = (int) Math.min((long) (page - 1) * pageSize, all.size());
        int to = (int) Math.min(from + (long) pageSize, all.size());
        ConsoleAudienceDetailListResponse resp = new ConsoleAudienceDetailListResponse();
        resp.setPage(page);
        resp.setPageSize(pageSize);
        resp.setTotal(all.size());
        resp.setList(new ArrayList<>(all.subList(from, to)));
        return resp;
    }

    @Transactional(readOnly = true)
    public byte[] exportCsv(
            String activityId,
            String keywordType,
            String keyword,
            String onlineStatus,
            Long followerEmployeeId,
            String followerKeyword,
            String sort,
            String order) {
        List<ConsoleAudienceDetailItem> all = prepareRows(
                activityId,
                keywordType,
                keyword,
                onlineStatus,
                followerEmployeeId,
                followerKeyword,
                sort,
                order);
        StringBuilder sb = new StringBuilder();
        sb.append("用户,用户ID,手机号,跟进人,首次进入时间,最近进入时间,直播观看时长,回放观看时长,累计观看时长,")
                .append("离线时间,进出次数,状态,点击商品次数,待支付次数,购买次数,累计购买次数,累计购买金额,评论次数,领券次数\n");
        for (ConsoleAudienceDetailItem item : all) {
            sb.append(csvCell(item.getNickname()))
                    .append(',')
                    .append(csvCell(item.getUsername()))
                    .append(',')
                    .append(csvCell(item.getPhoneMasked()))
                    .append(',')
                    .append(csvCell(item.getFollowerName()))
                    .append(',')
                    .append(csvCell(item.getFirstEnterTime()))
                    .append(',')
                    .append(csvCell(item.getLatestEnterTime()))
                    .append(',')
                    .append(formatDurationSec(item.getLiveWatchDurationSec()))
                    .append(',')
                    .append(formatDurationSec(item.getReplayWatchDurationSec()))
                    .append(',')
                    .append(formatDurationSec(item.getTotalWatchDurationSec()))
                    .append(',')
                    .append(csvCell(item.getLeaveTime()))
                    .append(',')
                    .append(item.getEnterCount())
                    .append(',')
                    .append(item.isOnline() ? "在线" : "离线")
                    .append(',')
                    .append(item.getProductClickCount())
                    .append(',')
                    .append(item.getPendingPayCount())
                    .append(',')
                    .append(item.getPurchaseCount())
                    .append(',')
                    .append(item.getTotalPurchaseCount())
                    .append(',')
                    .append(csvCell(item.getTotalPurchaseAmount()))
                    .append(',')
                    .append(item.getCommentCount())
                    .append(',')
                    .append(item.getCouponReceiveCount())
                    .append('\n');
        }
        return csvBytes(sb.toString());
    }

    private List<ConsoleAudienceDetailItem> prepareRows(
            String activityId,
            String keywordType,
            String keyword,
            String onlineStatus,
            Long followerEmployeeId,
            String followerKeyword,
            String sort,
            String order) {
        ActivityEntity activity = requireActivity(activityId);
        String uuid = activity.getId();
        String kwType = normalizeKeywordType(keywordType);
        String kw = keyword == null ? "" : keyword.trim();
        String status = normalizeOnlineStatus(onlineStatus);
        Long followerFilterId = normalizeFollowerEmployeeId(followerEmployeeId);
        String followerKw = followerKeyword == null ? "" : followerKeyword.trim();
        String sortKey = normalizeSort(sort);
        boolean asc = "asc".equalsIgnoreCase(order == null ? "desc" : order.trim());
        if (order != null && !order.isBlank()) {
            String o = order.trim().toLowerCase(Locale.ROOT);
            if (!"asc".equals(o) && !"desc".equals(o)) {
                throw new IllegalArgumentException("order 须为 asc 或 desc");
            }
        }

        List<PresenceEventEntity> events = presenceEventRepository.findByRoomId(uuid);
        Map<String, PresenceAgg> presenceByUser = aggregatePresence(events);
        if (presenceByUser.isEmpty()) {
            return List.of();
        }

        Set<String> usernames = new HashSet<>(presenceByUser.keySet());
        Set<String> keywordFilter = resolveKeywordUsernames(kwType, kw);
        if (keywordFilter != null) {
            usernames.retainAll(keywordFilter);
            if (usernames.isEmpty()) {
                return List.of();
            }
        }

        boolean forceOffline = isTerminal(activity.getState());
        Map<String, RoomAttendanceEntity> attendanceByUser = loadAttendance(uuid);
        Map<String, WatchDurationCalculator.Durations> durations = WatchDurationCalculator.byViewer(
                activity,
                events,
                replayWatchRepository.findByRoomId(uuid),
                liveSessionRepository.findByActivityId(uuid),
                LocalDateTime.ofInstant(clock.instant(), SHANGHAI));
        Map<String, ActivityAudienceEntity> audienceByUser = audienceRepository
                .findByActivityIdAndUsernameIn(uuid, usernames)
                .stream()
                .collect(Collectors.toMap(ActivityAudienceEntity::getUsername, a -> a, (a, b) -> a));
        Map<Long, String> employeeNames = loadEmployeeNames(audienceByUser.values());
        Map<String, ViewerEntity> viewers = loadViewers(usernames);
        Map<String, Long> clicks = toLongMap(
                productClickRepository.countByActivityAndUsernames(uuid, usernames));
        Map<String, long[]> orderLocal = loadLocalOrderCounts(uuid, usernames);
        Map<String, PaidAgg> paidAll = loadPaidAll(usernames);
        Map<String, Long> comments = toLongMap(
                commentRepository.countActiveByUsernames(uuid, usernames));

        List<ConsoleAudienceDetailItem> rows = new ArrayList<>();
        for (String username : usernames) {
            PresenceAgg presence = presenceByUser.get(username);
            if (presence == null || presence.enterCount <= 0) {
                continue;
            }
            RoomAttendanceEntity attendance = attendanceByUser.get(username);
            boolean online = !forceOffline && attendance != null && attendance.getLeaveTime() == null;
            if ("online".equals(status) && !online) {
                continue;
            }
            if ("offline".equals(status) && online) {
                continue;
            }

            ActivityAudienceEntity audience = audienceByUser.get(username);
            Long inviterId = audience == null ? null : audience.getEmployeeInviterId();
            String followerName = null;
            if (inviterId != null && inviterId > 0) {
                followerName = employeeNames.get(inviterId);
                if (followerName == null || followerName.isBlank()) {
                    followerName = "员工" + inviterId;
                }
            }
            if (followerFilterId != null) {
                if (inviterId == null || !followerFilterId.equals(inviterId)) {
                    continue;
                }
            }
            if (!followerKw.isEmpty()) {
                if (followerName == null || !followerName.contains(followerKw)) {
                    continue;
                }
            }

            ViewerEntity viewer = viewers.get(username);
            WatchDurationCalculator.Durations d =
                    durations.getOrDefault(username, WatchDurationCalculator.zero());
            long[] localOrders = orderLocal.getOrDefault(username, new long[] {0L, 0L});
            PaidAgg paid = paidAll.getOrDefault(username, PaidAgg.ZERO);

            ConsoleAudienceDetailItem item = new ConsoleAudienceDetailItem();
            item.setUsername(username);
            item.setNickname(
                    viewer != null
                            ? ViewerDisplayNames.displayNickname(viewer)
                            : ViewerDisplayNames.displayNicknameForUsername(username));
            if (viewer != null && viewer.getAvatarUrl() != null && !viewer.getAvatarUrl().isBlank()) {
                item.setAvatarUrl(viewer.getAvatarUrl().trim());
            }
            if (viewer != null && viewer.getPhone() != null && !viewer.getPhone().isBlank()) {
                item.setPhoneMasked(ViewerAdminService.maskPhone(viewer.getPhone()));
            }
            if (inviterId != null && inviterId > 0) {
                item.setFollowerEmployeeId(inviterId);
                item.setFollowerName(followerName);
            }
            item.setFirstEnterTime(AttendanceTimes.format(presence.firstEnter));
            item.setLatestEnterTime(AttendanceTimes.format(presence.latestEnter));
            item.setLiveWatchDurationSec(d.liveSec());
            item.setReplayWatchDurationSec(d.replaySec());
            item.setTotalWatchDurationSec(d.totalSec());
            if (!online && attendance != null && attendance.getLeaveTime() != null) {
                item.setLeaveTime(AttendanceTimes.format(attendance.getLeaveTime()));
            }
            item.setEnterCount(presence.enterCount);
            item.setOnline(online);
            item.setProductClickCount(clicks.getOrDefault(username, 0L).intValue());
            item.setPendingPayCount((int) localOrders[0]);
            item.setPurchaseCount((int) localOrders[1]);
            item.setTotalPurchaseCount(paid.count);
            item.setTotalPurchaseAmount(formatMoney(paid.amount));
            item.setCommentCount(comments.getOrDefault(username, 0L).intValue());
            item.setCouponReceiveCount(0);
            rows.add(item);
        }

        Comparator<ConsoleAudienceDetailItem> cmp = comparator(sortKey);
        if (!asc) {
            cmp = cmp.reversed();
        }
        rows.sort(cmp.thenComparing(ConsoleAudienceDetailItem::getUsername));
        return rows;
    }

    private static Map<String, PresenceAgg> aggregatePresence(List<PresenceEventEntity> events) {
        Map<String, PresenceAgg> map = new HashMap<>();
        for (PresenceEventEntity event : events) {
            String username = PresenceUserIds.viewerUsername(event.getUsername());
            if (username == null || event.getEventTime() == null) {
                continue;
            }
            if (!PresenceEventEntity.TYPE_JOIN.equals(event.getType())) {
                continue;
            }
            PresenceAgg agg = map.computeIfAbsent(username, k -> new PresenceAgg());
            agg.enterCount++;
            if (agg.firstEnter == null || event.getEventTime().isBefore(agg.firstEnter)) {
                agg.firstEnter = event.getEventTime();
            }
            if (agg.latestEnter == null || event.getEventTime().isAfter(agg.latestEnter)) {
                agg.latestEnter = event.getEventTime();
            }
        }
        return map;
    }

    private Map<String, RoomAttendanceEntity> loadAttendance(String roomId) {
        Map<String, RoomAttendanceEntity> map = new HashMap<>();
        for (RoomAttendanceEntity row : attendanceRepository.findByRoomId(roomId)) {
            String username = PresenceUserIds.viewerUsername(row.getUsername());
            if (username == null) {
                continue;
            }
            map.put(username, row);
        }
        return map;
    }

    /** @return null=不筛；空集合=无命中 */
    private Set<String> resolveKeywordUsernames(String type, String kw) {
        if (kw.isEmpty()) {
            return null;
        }
        return switch (type) {
            case "username" -> {
                if (kw.length() > 16) {
                    yield Set.of();
                }
                yield viewerRepository.findByUsername(kw).stream()
                        .map(ViewerEntity::getUsername)
                        .filter(u -> u != null && !u.isBlank())
                        .map(String::trim)
                        .collect(Collectors.toSet());
            }
            case "phone" -> viewerRepository
                    .findAll((root, query, cb) -> cb.like(root.get("phone"), "%" + kw + "%"))
                    .stream()
                    .map(ViewerEntity::getUsername)
                    .filter(u -> u != null && !u.isBlank())
                    .map(String::trim)
                    .collect(Collectors.toSet());
            default -> viewerRepository.findByNicknameContaining(kw).stream()
                    .map(ViewerEntity::getUsername)
                    .filter(u -> u != null && !u.isBlank())
                    .map(String::trim)
                    .collect(Collectors.toSet());
        };
    }

    private Map<String, ViewerEntity> loadViewers(Set<String> usernames) {
        if (usernames.isEmpty()) {
            return Map.of();
        }
        return viewerRepository.findByUsernameIn(usernames).stream()
                .collect(Collectors.toMap(ViewerEntity::getUsername, v -> v, (a, b) -> a));
    }

    private Map<Long, String> loadEmployeeNames(Iterable<ActivityAudienceEntity> rows) {
        Set<Long> ids = new HashSet<>();
        for (ActivityAudienceEntity row : rows) {
            if (row.getEmployeeInviterId() != null && row.getEmployeeInviterId() > 0) {
                ids.add(row.getEmployeeInviterId());
            }
        }
        if (ids.isEmpty()) {
            return Map.of();
        }
        Map<Long, String> map = new HashMap<>();
        for (SysUserAccountEntity u : userRepository.findAllById(ids)) {
            map.put(u.getId(), u.getUserName());
        }
        return map;
    }

    /** [0]=pending, [1]=paid this activity */
    private Map<String, long[]> loadLocalOrderCounts(String activityId, Set<String> usernames) {
        Map<String, long[]> map = new HashMap<>();
        if (usernames.isEmpty()) {
            return map;
        }
        for (Object[] row : orderRepository.countByActivityUsernameAndStatus(activityId, usernames)) {
            String username = (String) row[0];
            Integer status = (Integer) row[1];
            long count = ((Number) row[2]).longValue();
            long[] slot = map.computeIfAbsent(username, k -> new long[2]);
            if (com.zhibo.activity.ActivityOrderStatuses.isUnpaid(status)) {
                slot[0] += count;
            } else if (status != null && PAID_STATUSES.contains(status)) {
                slot[1] += count;
            }
        }
        return map;
    }

    private Map<String, PaidAgg> loadPaidAll(Set<String> usernames) {
        Map<String, PaidAgg> map = new HashMap<>();
        if (usernames.isEmpty()) {
            return map;
        }
        for (Object[] row : orderRepository.aggregatePaidByUsernames(usernames, PAID_STATUSES)) {
            String username = (String) row[0];
            int count = ((Number) row[1]).intValue();
            BigDecimal amount = row[2] instanceof BigDecimal bd ? bd : BigDecimal.ZERO;
            map.put(username, new PaidAgg(count, amount));
        }
        return map;
    }

    private static Map<String, Long> toLongMap(List<Object[]> rows) {
        Map<String, Long> map = new HashMap<>();
        for (Object[] row : rows) {
            if (row[0] == null) {
                continue;
            }
            map.put((String) row[0], ((Number) row[1]).longValue());
        }
        return map;
    }

    private ActivityEntity requireActivity(String id) {
        String uuid = UuidIds.require(id, "活动 id 无效", "活动 id 须为本站 UUID");
        return activityRepository
                .findById(uuid)
                .orElseThrow(() -> new IllegalArgumentException("活动不存在"));
    }

    private static boolean isTerminal(Integer state) {
        return state != null && (state == 3 || state == 4 || state == 5);
    }

    private static String normalizeKeywordType(String raw) {
        if (raw == null || raw.isBlank()) {
            return "nickname";
        }
        String value = raw.trim();
        if (!KEYWORD_TYPES.contains(value)) {
            throw new IllegalArgumentException("keywordType 无效");
        }
        return value;
    }

    private static String normalizeOnlineStatus(String raw) {
        if (raw == null || raw.isBlank()) {
            return "all";
        }
        String value = raw.trim();
        if (!ONLINE_STATUS.contains(value)) {
            throw new IllegalArgumentException("onlineStatus 无效");
        }
        return value;
    }

    private static Long normalizeFollowerEmployeeId(Long raw) {
        if (raw == null) {
            return null;
        }
        if (raw <= 0) {
            throw new IllegalArgumentException("followerEmployeeId 无效");
        }
        return raw;
    }

    private static String normalizeSort(String raw) {
        if (raw == null || raw.isBlank()) {
            return "latestEnterTime";
        }
        String value = raw.trim();
        if (!SORT_KEYS.contains(value)) {
            throw new IllegalArgumentException("sort 无效");
        }
        return value;
    }

    private static Comparator<ConsoleAudienceDetailItem> comparator(String sortKey) {
        return switch (sortKey) {
            case "firstEnterTime" -> Comparator.comparing(
                    ConsoleAudienceDetailItem::getFirstEnterTime, Comparator.nullsLast(String::compareTo));
            case "liveWatchDuration" -> Comparator.comparingLong(
                    ConsoleAudienceDetailItem::getLiveWatchDurationSec);
            case "replayWatchDuration" -> Comparator.comparingLong(
                    ConsoleAudienceDetailItem::getReplayWatchDurationSec);
            case "totalWatchDuration" -> Comparator.comparingLong(
                    ConsoleAudienceDetailItem::getTotalWatchDurationSec);
            case "leaveTime" -> Comparator.comparing(
                    ConsoleAudienceDetailItem::getLeaveTime, Comparator.nullsLast(String::compareTo));
            case "enterCount" -> Comparator.comparingInt(ConsoleAudienceDetailItem::getEnterCount);
            case "online" -> Comparator.comparing(ConsoleAudienceDetailItem::isOnline);
            case "productClickCount" -> Comparator.comparingInt(
                    ConsoleAudienceDetailItem::getProductClickCount);
            case "pendingPayCount" -> Comparator.comparingInt(
                    ConsoleAudienceDetailItem::getPendingPayCount);
            case "purchaseCount" -> Comparator.comparingInt(ConsoleAudienceDetailItem::getPurchaseCount);
            case "totalPurchaseCount" -> Comparator.comparingInt(
                    ConsoleAudienceDetailItem::getTotalPurchaseCount);
            case "totalPurchaseAmount" -> Comparator.comparing(
                    (ConsoleAudienceDetailItem r) -> parseMoney(r.getTotalPurchaseAmount()));
            case "commentCount" -> Comparator.comparingInt(ConsoleAudienceDetailItem::getCommentCount);
            case "couponReceiveCount" -> Comparator.comparingInt(
                    ConsoleAudienceDetailItem::getCouponReceiveCount);
            default -> Comparator.comparing(
                    ConsoleAudienceDetailItem::getLatestEnterTime, Comparator.nullsLast(String::compareTo));
        };
    }

    private static BigDecimal parseMoney(String raw) {
        if (raw == null || raw.isBlank()) {
            return BigDecimal.ZERO;
        }
        try {
            return new BigDecimal(raw.trim());
        } catch (NumberFormatException ex) {
            return BigDecimal.ZERO;
        }
    }

    private static String formatMoney(BigDecimal amount) {
        BigDecimal value = amount == null ? BigDecimal.ZERO : amount;
        return value.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    private static String formatDurationSec(long sec) {
        long n = Math.max(0, sec);
        long h = n / 3600;
        long m = (n % 3600) / 60;
        long s = n % 60;
        return String.format("%02d:%02d:%02d", h, m, s);
    }

    private static String csvCell(String raw) {
        String s = raw == null ? "" : raw;
        if (s.indexOf(',') >= 0 || s.indexOf('"') >= 0 || s.indexOf('\n') >= 0 || s.indexOf('\r') >= 0) {
            return '"' + s.replace("\"", "\"\"") + '"';
        }
        return s;
    }

    private static byte[] csvBytes(String body) {
        byte[] raw = body.getBytes(StandardCharsets.UTF_8);
        byte[] out = new byte[UTF8_BOM.length + raw.length];
        System.arraycopy(UTF8_BOM, 0, out, 0, UTF8_BOM.length);
        System.arraycopy(raw, 0, out, UTF8_BOM.length, raw.length);
        return out;
    }

    private static final class PresenceAgg {
        LocalDateTime firstEnter;
        LocalDateTime latestEnter;
        int enterCount;
    }

    private record PaidAgg(int count, BigDecimal amount) {
        static final PaidAgg ZERO = new PaidAgg(0, BigDecimal.ZERO);
    }
}
