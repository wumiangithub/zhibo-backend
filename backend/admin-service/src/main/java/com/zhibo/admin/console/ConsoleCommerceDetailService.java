package com.zhibo.admin.console;

import com.zhibo.activity.ActivityAudienceEntity;
import com.zhibo.activity.ActivityAudienceRepository;
import com.zhibo.activity.ActivityEntity;
import com.zhibo.activity.ActivityOrderEntity;
import com.zhibo.activity.ActivityOrderRepository;
import com.zhibo.activity.ActivityProductClickRepository;
import com.zhibo.activity.ActivityRepository;
import com.zhibo.activity.AttendanceTimes;
import com.zhibo.admin.auth.SysUserAccountEntity;
import com.zhibo.admin.auth.SysUserAccountRepository;
import com.zhibo.admin.console.dto.ConsoleCommerceDetailItem;
import com.zhibo.admin.console.dto.ConsoleCommerceDetailListResponse;
import com.zhibo.admin.console.dto.ConsoleSessionInviterItem;
import com.zhibo.admin.console.dto.ConsoleSessionInviterListResponse;
import com.zhibo.admin.viewer.ViewerAdminService;
import com.zhibo.common.UuidIds;
import com.zhibo.viewer.ViewerDisplayNames;
import com.zhibo.viewer.ViewerEntity;
import com.zhibo.viewer.ViewerRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.text.Collator;
import java.time.LocalDateTime;
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
 * 中控台底部带货明细（D124）：本场订单按用户×商品聚合；点击仅挂指标。
 */
@Service
@RequiredArgsConstructor
public class ConsoleCommerceDetailService {

    private static final byte[] UTF8_BOM = new byte[] {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};
    private static final Set<String> KEYWORD_TYPES = Set.of("nickname", "username", "phone");
    private static final Set<String> PAY_STATUSES =
            Set.of("all", CommercePayStatus.UNPAID, CommercePayStatus.PAID, CommercePayStatus.REFUNDED);
    private static final Set<String> SORT_KEYS = Set.of(
            "clickCount",
            "lastClickTime",
            "orderCount",
            "unpaidCount",
            "paidSuccessCount",
            "liveDealAmount",
            "totalPaidOrderCount",
            "totalPaidAmount",
            "productName",
            "payStatus");
    private static final List<Integer> PAID_STATUSES =
            com.zhibo.activity.ActivityOrderStatuses.PAID_STATUSES;

    private final ActivityRepository activityRepository;
    private final ActivityOrderRepository orderRepository;
    private final ActivityProductClickRepository productClickRepository;
    private final ActivityAudienceRepository audienceRepository;
    private final ViewerRepository viewerRepository;
    private final SysUserAccountRepository userRepository;

    @Transactional(readOnly = true)
    public ConsoleSessionInviterListResponse sessionInviters(String activityId) {
        ActivityEntity activity = requireActivity(activityId);
        ConsoleSessionInviterListResponse resp = new ConsoleSessionInviterListResponse();
        resp.setId(activity.getId());
        List<Long> ids = audienceRepository.findDistinctEmployeeInviterIds(activity.getId());
        if (ids.isEmpty()) {
            return resp;
        }
        Map<Long, String> names = new HashMap<>();
        for (SysUserAccountEntity u : userRepository.findAllById(ids)) {
            names.put(u.getId(), u.getUserName());
        }
        List<ConsoleSessionInviterItem> list = new ArrayList<>();
        for (Long id : ids) {
            ConsoleSessionInviterItem item = new ConsoleSessionInviterItem();
            item.setEmployeeId(id);
            String name = names.get(id);
            item.setName(name != null && !name.isBlank() ? name.trim() : "员工" + id);
            list.add(item);
        }
        Collator zh = Collator.getInstance(Locale.CHINA);
        list.sort(Comparator.comparing(ConsoleSessionInviterItem::getName, zh));
        resp.setList(list);
        return resp;
    }

    @Transactional(readOnly = true)
    public ConsoleCommerceDetailListResponse list(
            String activityId,
            int page,
            int pageSize,
            String productName,
            String payStatus,
            Long followerEmployeeId,
            String followerKeyword,
            String keywordType,
            String keyword,
            String sort,
            String order) {
        if (page < 1) {
            throw new IllegalArgumentException("page 不能小于 1");
        }
        if (pageSize < 1 || pageSize > 100) {
            throw new IllegalArgumentException("pageSize 需在 1~100");
        }
        List<ConsoleCommerceDetailItem> all = prepareRows(
                activityId,
                productName,
                payStatus,
                followerEmployeeId,
                followerKeyword,
                keywordType,
                keyword,
                sort,
                order);
        int from = (int) Math.min((long) (page - 1) * pageSize, all.size());
        int to = (int) Math.min(from + (long) pageSize, all.size());
        ConsoleCommerceDetailListResponse resp = new ConsoleCommerceDetailListResponse();
        resp.setPage(page);
        resp.setPageSize(pageSize);
        resp.setTotal(all.size());
        resp.setList(new ArrayList<>(all.subList(from, to)));
        return resp;
    }

    @Transactional(readOnly = true)
    public byte[] exportCsv(
            String activityId,
            String productName,
            String payStatus,
            Long followerEmployeeId,
            String followerKeyword,
            String keywordType,
            String keyword,
            String sort,
            String order) {
        List<ConsoleCommerceDetailItem> all = prepareRows(
                activityId,
                productName,
                payStatus,
                followerEmployeeId,
                followerKeyword,
                keywordType,
                keyword,
                sort,
                order);
        StringBuilder sb = new StringBuilder();
        sb.append("用户,用户ID,unionid,商品名称,点击次数,最近点击,下单次数,未支付订单数,支付成功订单数,支付状态,")
                .append("直播间成交金额,总支付订单,总成交金额,手机号,跟进人\n");
        for (ConsoleCommerceDetailItem item : all) {
            sb.append(csvCell(item.getNickname()))
                    .append(',')
                    .append(csvCell(item.getUsername()))
                    .append(',')
                    .append(csvCell(item.getUnionid()))
                    .append(',')
                    .append(csvCell(item.getProductName()))
                    .append(',')
                    .append(item.getClickCount())
                    .append(',')
                    .append(csvCell(item.getLastClickTime()))
                    .append(',')
                    .append(item.getOrderCount())
                    .append(',')
                    .append(item.getUnpaidCount())
                    .append(',')
                    .append(item.getPaidSuccessCount())
                    .append(',')
                    .append(csvCell(CommercePayStatus.labelZh(item.getPayStatus())))
                    .append(',')
                    .append(csvCell(item.getLiveDealAmount()))
                    .append(',')
                    .append(item.getTotalPaidOrderCount())
                    .append(',')
                    .append(csvCell(item.getTotalPaidAmount()))
                    .append(',')
                    .append(csvCell(item.getPhoneMasked()))
                    .append(',')
                    .append(csvCell(item.getFollowerName()))
                    .append('\n');
        }
        return csvBytes(sb.toString());
    }

    private List<ConsoleCommerceDetailItem> prepareRows(
            String activityId,
            String productName,
            String payStatus,
            Long followerEmployeeId,
            String followerKeyword,
            String keywordType,
            String keyword,
            String sort,
            String order) {
        ActivityEntity activity = requireActivity(activityId);
        String uuid = activity.getId();
        String productKw = productName == null ? "" : productName.trim();
        String statusFilter = normalizePayStatus(payStatus);
        Long followerId = normalizeFollowerEmployeeId(followerEmployeeId);
        String followerKw = followerKeyword == null ? "" : followerKeyword.trim();
        String kwType = normalizeKeywordType(keywordType);
        String kw = keyword == null ? "" : keyword.trim();
        String sortKey = normalizeSort(sort);
        boolean asc = "asc".equalsIgnoreCase(order == null ? "desc" : order.trim());
        if (order != null && !order.isBlank()) {
            String o = order.trim().toLowerCase(Locale.ROOT);
            if (!"asc".equals(o) && !"desc".equals(o)) {
                throw new IllegalArgumentException("order 须为 asc 或 desc");
            }
        }

        List<ActivityOrderEntity> orders = orderRepository.findByActivityIdWithProduct(uuid);
        if (orders.isEmpty()) {
            return List.of();
        }

        Map<String, Agg> byPair = new HashMap<>();
        for (ActivityOrderEntity o : orders) {
            String username = o.getUsername() == null ? "" : o.getUsername().trim();
            if (username.isEmpty() || o.getProductId() == null) {
                continue;
            }
            String spuId = Long.toString(o.getProductId());
            String key = username + '\0' + spuId;
            Agg agg = byPair.computeIfAbsent(key, k -> new Agg(username, spuId));
            agg.orderCount++;
            Integer st = o.getOrderStatus();
            if (st != null) {
                agg.statuses.add(st);
                if (com.zhibo.activity.ActivityOrderStatuses.isUnpaid(st)) {
                    agg.unpaidCount++;
                } else if (PAID_STATUSES.contains(st)) {
                    agg.paidSuccessCount++;
                    BigDecimal amt = o.getPayAmount() == null ? BigDecimal.ZERO : o.getPayAmount();
                    agg.liveDealAmount = agg.liveDealAmount.add(amt);
                }
            }
            String pname = o.getProductName();
            LocalDateTime ot = o.getOrderTime() != null ? o.getOrderTime() : o.getCreateTime();
            if (pname != null && !pname.isBlank()) {
                if (agg.productNameTime == null || (ot != null && ot.isAfter(agg.productNameTime))) {
                    agg.productName = pname.trim();
                    agg.productNameTime = ot;
                }
            }
        }
        if (byPair.isEmpty()) {
            return List.of();
        }

        Set<String> usernames = byPair.values().stream().map(a -> a.username).collect(Collectors.toSet());
        Set<String> keywordFilter = resolveKeywordUsernames(kwType, kw);
        if (keywordFilter != null) {
            usernames.retainAll(keywordFilter);
            if (usernames.isEmpty()) {
                return List.of();
            }
        }

        Map<String, ClickAgg> clicks = loadClicks(uuid, usernames);
        Map<String, ActivityAudienceEntity> audienceByUser = audienceRepository
                .findByActivityIdAndUsernameIn(uuid, usernames)
                .stream()
                .collect(Collectors.toMap(ActivityAudienceEntity::getUsername, a -> a, (a, b) -> a));
        Map<Long, String> employeeNames = loadEmployeeNames(audienceByUser.values());
        Map<String, ViewerEntity> viewers = loadViewers(usernames);
        Map<String, PaidAgg> paidAll = loadPaidAll(usernames);

        List<ConsoleCommerceDetailItem> rows = new ArrayList<>();
        for (Agg agg : byPair.values()) {
            if (!usernames.contains(agg.username)) {
                continue;
            }
            if (!productKw.isEmpty()) {
                String pn = agg.productName == null ? "" : agg.productName;
                if (!pn.contains(productKw)) {
                    continue;
                }
            }
            String derived = CommercePayStatus.derive(agg.statuses);
            if (!"all".equals(statusFilter) && !statusFilter.equals(derived)) {
                continue;
            }

            ActivityAudienceEntity audience = audienceByUser.get(agg.username);
            Long inviterId = audience == null ? null : audience.getEmployeeInviterId();
            String followerName = null;
            if (inviterId != null && inviterId > 0) {
                followerName = employeeNames.get(inviterId);
                if (followerName == null || followerName.isBlank()) {
                    followerName = "员工" + inviterId;
                }
            }
            if (followerId != null) {
                if (inviterId == null || !followerId.equals(inviterId)) {
                    continue;
                }
            }
            if (!followerKw.isEmpty()) {
                if (followerName == null || !followerName.contains(followerKw)) {
                    continue;
                }
            }

            ViewerEntity viewer = viewers.get(agg.username);
            PaidAgg paid = paidAll.getOrDefault(agg.username, PaidAgg.ZERO);
            ClickAgg click = clicks.getOrDefault(agg.username + '\0' + agg.spuId, ClickAgg.ZERO);

            ConsoleCommerceDetailItem item = new ConsoleCommerceDetailItem();
            item.setUsername(agg.username);
            item.setNickname(
                    viewer != null
                            ? ViewerDisplayNames.displayNickname(viewer)
                            : ViewerDisplayNames.displayNicknameForUsername(agg.username));
            if (viewer != null && viewer.getAvatarUrl() != null && !viewer.getAvatarUrl().isBlank()) {
                item.setAvatarUrl(viewer.getAvatarUrl().trim());
            }
            if (viewer != null && viewer.getUnionid() != null && !viewer.getUnionid().isBlank()) {
                item.setUnionid(viewer.getUnionid().trim());
            }
            item.setSpuId(agg.spuId);
            item.setProductName(agg.productName == null ? "" : agg.productName);
            item.setClickCount(click.count);
            if (click.lastClick != null) {
                item.setLastClickTime(AttendanceTimes.format(click.lastClick));
            }
            item.setOrderCount(agg.orderCount);
            item.setUnpaidCount(agg.unpaidCount);
            item.setPaidSuccessCount(agg.paidSuccessCount);
            item.setPayStatus(derived);
            item.setLiveDealAmount(formatMoney(agg.liveDealAmount));
            item.setTotalPaidOrderCount(paid.count);
            item.setTotalPaidAmount(formatMoney(paid.amount));
            if (viewer != null && viewer.getPhone() != null && !viewer.getPhone().isBlank()) {
                item.setPhoneMasked(ViewerAdminService.maskPhone(viewer.getPhone()));
            }
            if (inviterId != null && inviterId > 0) {
                item.setFollowerEmployeeId(inviterId);
                item.setFollowerName(followerName);
            }
            rows.add(item);
        }

        Comparator<ConsoleCommerceDetailItem> cmp = comparator(sortKey);
        if (!asc) {
            cmp = cmp.reversed();
        }
        rows.sort(cmp.thenComparing(ConsoleCommerceDetailItem::getUsername)
                .thenComparing(ConsoleCommerceDetailItem::getSpuId));
        return rows;
    }

    private Map<String, ClickAgg> loadClicks(String activityId, Set<String> usernames) {
        Map<String, ClickAgg> map = new HashMap<>();
        if (usernames.isEmpty()) {
            return map;
        }
        for (Object[] row : productClickRepository.aggregateByUsernameAndSpu(activityId, usernames)) {
            String username = (String) row[0];
            String spuId = (String) row[1];
            int count = ((Number) row[2]).intValue();
            LocalDateTime last = row[3] instanceof LocalDateTime t ? t : null;
            map.put(username + '\0' + spuId, new ClickAgg(count, last));
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

    private ActivityEntity requireActivity(String id) {
        String uuid = UuidIds.require(id, "活动 id 无效", "活动 id 须为本站 UUID");
        return activityRepository
                .findById(uuid)
                .orElseThrow(() -> new IllegalArgumentException("活动不存在"));
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

    private static String normalizePayStatus(String raw) {
        if (raw == null || raw.isBlank()) {
            return "all";
        }
        String value = raw.trim();
        if (!PAY_STATUSES.contains(value)) {
            throw new IllegalArgumentException("payStatus 无效");
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
            return "orderCount";
        }
        String value = raw.trim();
        if (!SORT_KEYS.contains(value)) {
            throw new IllegalArgumentException("sort 无效");
        }
        return value;
    }

    private static Comparator<ConsoleCommerceDetailItem> comparator(String sortKey) {
        return switch (sortKey) {
            case "clickCount" -> Comparator.comparingInt(ConsoleCommerceDetailItem::getClickCount);
            case "lastClickTime" -> Comparator.comparing(
                    ConsoleCommerceDetailItem::getLastClickTime, Comparator.nullsLast(String::compareTo));
            case "unpaidCount" -> Comparator.comparingInt(ConsoleCommerceDetailItem::getUnpaidCount);
            case "paidSuccessCount" -> Comparator.comparingInt(ConsoleCommerceDetailItem::getPaidSuccessCount);
            case "liveDealAmount" -> Comparator.comparing(
                    (ConsoleCommerceDetailItem r) -> parseMoney(r.getLiveDealAmount()));
            case "totalPaidOrderCount" -> Comparator.comparingInt(
                    ConsoleCommerceDetailItem::getTotalPaidOrderCount);
            case "totalPaidAmount" -> Comparator.comparing(
                    (ConsoleCommerceDetailItem r) -> parseMoney(r.getTotalPaidAmount()));
            case "productName" -> Comparator.comparing(
                    ConsoleCommerceDetailItem::getProductName, Comparator.nullsLast(String::compareTo));
            case "payStatus" -> Comparator.comparing(
                    ConsoleCommerceDetailItem::getPayStatus, Comparator.nullsLast(String::compareTo));
            default -> Comparator.comparingInt(ConsoleCommerceDetailItem::getOrderCount);
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

    private static final class Agg {
        final String username;
        final String spuId;
        final Set<Integer> statuses = new HashSet<>();
        String productName = "";
        LocalDateTime productNameTime;
        int orderCount;
        int unpaidCount;
        int paidSuccessCount;
        BigDecimal liveDealAmount = BigDecimal.ZERO;

        Agg(String username, String spuId) {
            this.username = username;
            this.spuId = spuId;
        }
    }

    private record ClickAgg(int count, LocalDateTime lastClick) {
        static final ClickAgg ZERO = new ClickAgg(0, null);
    }

    private record PaidAgg(int count, BigDecimal amount) {
        static final PaidAgg ZERO = new PaidAgg(0, BigDecimal.ZERO);
    }
}
