package com.zhibo.admin.console;

import com.zhibo.activity.ActivityCouponEntity;
import com.zhibo.activity.ActivityCouponRepository;
import com.zhibo.activity.ActivityEntity;
import com.zhibo.activity.ActivityMemberEntity;
import com.zhibo.activity.ActivityMemberId;
import com.zhibo.activity.ActivityMemberRepository;
import com.zhibo.activity.ActivityRepository;
import com.zhibo.activity.RoomCommentEntity;
import com.zhibo.activity.RoomCommentRepository;
import com.zhibo.activity.RoomCommentType;
import com.zhibo.activity.RoomCouponContent;
import com.zhibo.admin.auth.SysUserAccountEntity;
import com.zhibo.admin.auth.SysUserAccountRepository;
import com.zhibo.admin.console.dto.ConsoleCouponHistoryItem;
import com.zhibo.admin.console.dto.ConsoleCouponHistoryResponse;
import com.zhibo.admin.console.dto.ConsoleSendCouponRequest;
import com.zhibo.admin.console.dto.ConsoleSendCouponResponse;
import com.zhibo.admin.org.EmployeeDeptResolver;
import com.zhibo.common.UuidIds;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 中控台发券 + 券聊天历史（D113）。 */
@Service
@RequiredArgsConstructor
public class ConsoleCouponService {

    private static final ZoneId SHANGHAI = ZoneId.of("Asia/Shanghai");
    private static final DateTimeFormatter COMMENT_TIME =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    static final int MAX_COUPON_HISTORY = 500;

    private final ActivityRepository activityRepository;
    private final ActivityMemberRepository memberRepository;
    private final SysUserAccountRepository userRepository;
    private final ActivityCouponRepository activityCouponRepository;
    private final RoomCommentRepository commentRepository;
    private final EmployeeDeptResolver employeeDeptResolver;
    private final Clock clock;

    @Transactional
    public ConsoleSendCouponResponse send(String activityId, long employeeId, ConsoleSendCouponRequest request) {
        if (employeeId <= 0) {
            throw new IllegalArgumentException("请先登录");
        }
        ActivityEntity activity = requireActivity(activityId);
        ValidCoupon body = validate(request);

        SysUserAccountEntity employee = userRepository.findById(employeeId)
                .orElseThrow(() -> new IllegalArgumentException("当前员工不存在"));
        // 领券传订单中心：hr_uimsyhxx.rs_mainkey_id（账号 yhid），不是登录账号主键
        Long hrEmployeeId = employee.getYhid();
        if (hrEmployeeId == null || hrEmployeeId <= 0) {
            throw new IllegalArgumentException("当前员工未绑定 CRM 档案，无法发券");
        }
        ActivityMemberEntity member = memberRepository
                .findById(new ActivityMemberId(activity.getId(), employeeId))
                .orElse(null);
        String nickname = ConsoleService.resolveConsoleNickname(null, member, employee);
        EmployeeDeptResolver.Dept senderDept = employeeDeptResolver.resolvePrimary(employeeId).orElse(null);
        Long deptId = senderDept == null ? null : senderDept.deptId();
        String deptName = senderDept == null ? null : senderDept.deptName();

        LocalDateTime now = LocalDateTime.now(clock.withZone(SHANGHAI));

        ActivityCouponEntity row = activityCouponRepository
                .findByActivityIdAndCouponId(activity.getId(), body.couponId())
                .orElseGet(ActivityCouponEntity::new);
        boolean creating = row.getId() == null;
        if (creating) {
            row.setActivityId(activity.getId());
            row.setCouponId(body.couponId());
        }
        row.setCouponNo(body.couponNo());
        row.setCouponName(body.couponName());
        row.setCouponAmount(body.couponAmount());
        row.setCouponStartDate(body.couponStartDate());
        row.setCouponEndDate(body.couponEndDate());
        row.setAllSku(body.allSku());
        row.setDeptNames(body.deptNamesJoined());
        row.setSkuNames(body.allSku() ? "" : body.skuNamesJoined());
        row.setLastSenderEmployeeId(hrEmployeeId);
        row.setLastSenderNickname(nickname);
        row.setLastSenderDeptId(deptId);
        row.setLastSenderDeptName(deptName);
        row.setLastSentTime(now);
        activityCouponRepository.save(row);

        RoomCouponContent snapshot = new RoomCouponContent(
                body.couponId(),
                body.couponNo(),
                body.couponName(),
                body.couponAmount(),
                body.couponStartDate(),
                body.couponEndDate(),
                hrEmployeeId,
                deptId,
                deptName);
        String contentJson = snapshot.toJson();
        if (contentJson.length() > 2000) {
            throw new IllegalArgumentException("优惠券信息过长");
        }

        RoomCommentEntity comment = new RoomCommentEntity();
        comment.setActivityId(activity.getId());
        comment.setProvider(activity.getProvider());
        comment.setRoomId(trimToNull(activity.getProviderRoomId()));
        comment.setContent(contentJson);
        comment.setNickname(nickname);
        comment.setCommentType(RoomCommentType.COUPON);
        comment.setCommentTime(now);
        commentRepository.save(comment);

        ConsoleSendCouponResponse response = new ConsoleSendCouponResponse();
        response.setCommentId(comment.getId());
        response.setCouponId(body.couponId());
        response.setCouponNo(body.couponNo());
        response.setCouponName(body.couponName());
        response.setCouponAmount(body.couponAmount());
        response.setCouponStartDate(body.couponStartDate());
        response.setCouponEndDate(body.couponEndDate());
        response.setEmployeeId(hrEmployeeId);
        response.setNickname(nickname);
        response.setDeptId(deptId);
        response.setDeptName(deptName);
        response.setCommentTime(COMMENT_TIME.format(now));
        return response;
    }

    @Transactional(readOnly = true)
    public ConsoleCouponHistoryResponse history(String activityId) {
        ActivityEntity activity = requireActivity(activityId);
        List<RoomCommentEntity> rows =
                commentRepository.findByActivityIdAndCommentTypeAndDeletedTimeIsNullOrderByCommentTimeAsc(
                        activity.getId(), RoomCommentType.COUPON);

        ConsoleCouponHistoryResponse response = new ConsoleCouponHistoryResponse();
        int limit = Math.min(rows.size(), MAX_COUPON_HISTORY);
        for (int i = 0; i < limit; i++) {
            ConsoleCouponHistoryItem item = toHistoryItem(rows.get(i));
            if (item != null) {
                response.getList().add(item);
            }
        }
        return response;
    }

    static ConsoleCouponHistoryItem toHistoryItem(RoomCommentEntity c) {
        RoomCouponContent parsed = RoomCouponContent.parse(c.getContent());
        if (parsed == null) {
            return null;
        }
        String nick = trimToNull(c.getNickname());
        if (nick == null) {
            return null;
        }
        ConsoleCouponHistoryItem item = new ConsoleCouponHistoryItem();
        item.setId(c.getId());
        item.setSenderName(nick);
        item.setTimestamp(toEpochMillis(c.getCommentTime()));
        item.setCouponId(parsed.couponId());
        item.setCouponName(parsed.couponName());
        item.setCouponAmount(parsed.couponAmount());
        item.setCouponStartDate(parsed.couponStartDate());
        item.setCouponEndDate(parsed.couponEndDate());
        item.setEmployeeId(parsed.employeeId());
        item.setDeptId(parsed.deptId());
        item.setDeptName(parsed.deptName());
        return item;
    }

    private ActivityEntity requireActivity(String id) {
        String uuid = UuidIds.require(id, "活动 id 无效", "活动 id 须为本站 UUID");
        ActivityEntity entity = activityRepository.findById(uuid)
                .orElseThrow(() -> new IllegalArgumentException("活动不存在"));
        if (entity.getDeletedTime() != null) {
            throw new IllegalArgumentException("直播不存在");
        }
        return entity;
    }

    private static ValidCoupon validate(ConsoleSendCouponRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("请求体不能为空");
        }
        Long couponId = request.getCouponId();
        if (couponId == null || couponId <= 0) {
            throw new IllegalArgumentException("couponId 无效");
        }
        String couponNo = requireText(request.getCouponNo(), "couponNo", 64);
        String couponName = requireText(request.getCouponName(), "couponName", 256);
        String couponAmount = requireText(request.getCouponAmount(), "couponAmount", 32);
        String start = requireText(request.getCouponStartDate(), "couponStartDate", 32);
        String end = requireText(request.getCouponEndDate(), "couponEndDate", 32);
        boolean allSku = Boolean.TRUE.equals(request.getAllSku());
        String deptJoined = joinNames(request.getDeptNames(), 512);
        String skuJoined = joinNames(request.getSkuNames(), 1024);
        return new ValidCoupon(
                couponId, couponNo, couponName, couponAmount, start, end, allSku, deptJoined, skuJoined);
    }

    private static String requireText(String raw, String field, int max) {
        String t = trimToNull(raw);
        if (t == null) {
            throw new IllegalArgumentException(field + " 不能为空");
        }
        if (t.length() > max) {
            throw new IllegalArgumentException(field + " 过长");
        }
        return t;
    }

    private static String joinNames(List<String> names, int maxLen) {
        if (names == null || names.isEmpty()) {
            return "";
        }
        List<String> cleaned = new ArrayList<>();
        for (String n : names) {
            String t = trimToNull(n);
            if (t != null) {
                cleaned.add(t);
            }
        }
        if (cleaned.isEmpty()) {
            return "";
        }
        String joined = String.join("、", cleaned);
        if (joined.length() <= maxLen) {
            return joined;
        }
        return joined.substring(0, maxLen);
    }

    private static Long toEpochMillis(LocalDateTime time) {
        if (time == null) {
            return System.currentTimeMillis();
        }
        return time.atZone(SHANGHAI).toInstant().toEpochMilli();
    }

    private static String trimToNull(String raw) {
        if (raw == null) {
            return null;
        }
        String t = raw.trim();
        return t.isEmpty() ? null : t;
    }

    private record ValidCoupon(
            Long couponId,
            String couponNo,
            String couponName,
            String couponAmount,
            String couponStartDate,
            String couponEndDate,
            boolean allSku,
            String deptNamesJoined,
            String skuNamesJoined) {}
}
