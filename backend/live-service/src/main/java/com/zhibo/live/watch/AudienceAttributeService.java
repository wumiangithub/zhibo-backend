package com.zhibo.live.watch;

import com.zhibo.activity.ActivityAudienceEntity;
import com.zhibo.activity.ActivityAudienceRepository;
import com.zhibo.activity.ActivityEntity;
import com.zhibo.activity.ActivityRepository;
import com.zhibo.common.UuidIds;
import com.zhibo.live.org.EmployeeAccountRepository;
import com.zhibo.live.org.HrEmployeePostEntity;
import com.zhibo.live.org.HrEmployeePostRepository;
import com.zhibo.live.org.HrEmployeeRepository;
import com.zhibo.live.watch.dto.AttributeAudienceRequest;
import com.zhibo.live.watch.dto.AttributeAudienceResponse;
import com.zhibo.live.watch.dto.AudienceSalesmanResponse;
import com.zhibo.viewer.ViewerRepository;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 观看分享链邀请归因（D77 / D97）与本场业务员解析。
 * 被邀请观众 id 由 Controller 从观众 JWT 传入；{@code inviterKind=employee} 写本场员工邀请人；
 * {@code audience} 观众互邀本站不落库（分支保留）。
 * 下单：仅员工邀请人 → {@code salesmanId}=identifier、{@code salesmanDeptId}=岗位 bmid；不传导入人。
 */
@Service
@RequiredArgsConstructor
public class AudienceAttributeService {

    static final String KIND_EMPLOYEE = "employee";
    static final String KIND_AUDIENCE = "audience";

    private final ActivityRepository activityRepository;
    private final ActivityAudienceRepository audienceRepository;
    private final ViewerRepository viewerRepository;
    private final EmployeeAccountRepository employeeAccountRepository;
    private final HrEmployeeRepository hrEmployeeRepository;
    private final HrEmployeePostRepository hrEmployeePostRepository;

    /**
     * 解析本场业务员：只认 {@code employee_inviter_id} → 账号 yhid →
     * {@code hr_uimsyhxx.identifier} + 在职岗位最小 id 的 {@code bmid}。
     * 无邀请人 / 无 identifier → 整段 null；有 identifier 无岗位 → dept 为 null。
     * <p>不回退导入人。
     */
    @Transactional(readOnly = true)
    public AudienceSalesmanResponse resolveSalesman(String activityId, long viewerId) {
        if (viewerId <= 0) {
            throw new IllegalArgumentException("uid 无效");
        }
        ActivityEntity activity = requireActivity(activityId);
        return audienceRepository
                .findByActivityIdAndUid(activity.getId(), viewerId)
                .map(this::pickSalesman)
                .orElseGet(() -> new AudienceSalesmanResponse(null, null));
    }

    /** 仅邀请人；不读导入人。 */
    AudienceSalesmanResponse pickSalesman(ActivityAudienceEntity row) {
        Long inviterAccountId = row.getEmployeeInviterId();
        if (inviterAccountId == null || inviterAccountId <= 0) {
            return new AudienceSalesmanResponse(null, null);
        }
        Optional<Long> yhidOpt = employeeAccountRepository
                .findById(inviterAccountId)
                .map(acc -> acc.getYhid())
                .filter(yhid -> yhid != null && yhid > 0);
        if (yhidOpt.isEmpty()) {
            return new AudienceSalesmanResponse(null, null);
        }
        long yhid = yhidOpt.get();
        String identifier = hrEmployeeRepository
                .findById(yhid)
                .map(hr -> hr.getIdentifier())
                .filter(id -> id != null && !id.isBlank())
                .map(String::trim)
                .orElse(null);
        if (identifier == null) {
            return new AudienceSalesmanResponse(null, null);
        }
        Long deptId = resolvePrimaryDeptId(yhid);
        return new AudienceSalesmanResponse(identifier, deptId);
    }

    /** 在职岗位取 id 最小一条的 bmid；对齐管理端发券部门口径。 */
    private Long resolvePrimaryDeptId(long yhid) {
        List<HrEmployeePostEntity> posts = hrEmployeePostRepository.findActiveWithDeptByYhid(yhid);
        if (posts == null || posts.isEmpty()) {
            return null;
        }
        return posts.stream()
                .min(Comparator.comparing(
                        HrEmployeePostEntity::getId, Comparator.nullsLast(Long::compareTo)))
                .map(HrEmployeePostEntity::getBmid)
                .orElse(null);
    }

    @Transactional
    public AttributeAudienceResponse attribute(
            String activityId, long viewerId, AttributeAudienceRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("请求体不能为空");
        }
        if (viewerId <= 0) {
            throw new IllegalArgumentException("uid 无效");
        }
        ActivityEntity activity = requireActivity(activityId);
        if (!viewerRepository.existsByUid(viewerId)) {
            throw new IllegalArgumentException("观众不存在");
        }
        Long inviterId = request.getInviterId();
        if (inviterId == null || inviterId <= 0) {
            throw new IllegalArgumentException("inviterId 无效");
        }

        String kind = normalizeKind(request.getInviterKind());
        if (KIND_AUDIENCE.equals(kind)) {
            throw new IllegalArgumentException("不记录观众邀请");
        }
        if (!KIND_EMPLOYEE.equals(kind)) {
            throw new IllegalArgumentException("inviterKind 须为 employee 或 audience");
        }
        return attributeEmployee(activity, viewerId, inviterId);
    }

    private AttributeAudienceResponse attributeEmployee(
            ActivityEntity activity, long viewerId, Long inviterId) {
        java.time.LocalDateTime now = java.time.LocalDateTime.now();
        ActivityAudienceEntity row = audienceRepository
                .findByActivityIdAndUid(activity.getId(), viewerId)
                .orElseGet(() -> {
                    ActivityAudienceEntity created = new ActivityAudienceEntity();
                    created.setActivityId(activity.getId());
                    created.setUid(viewerId);
                    created.setEntitlementStatus(1);
                    return created;
                });

        if (row.getEmployeeInviterId() == null) {
            row.setEmployeeInviterId(inviterId);
            row.setEmployeeInviteTime(now);
        }

        audienceRepository.save(row);
        return new AttributeAudienceResponse(row.getId(), row.getUid());
    }

    static String normalizeKind(String raw) {
        if (raw == null || raw.isBlank()) {
            return KIND_EMPLOYEE;
        }
        return raw.trim().toLowerCase(Locale.ROOT);
    }

    private ActivityEntity requireActivity(String activityId) {
        String uuid = UuidIds.require(activityId, "活动 id 无效", "活动 id 须为本站 UUID");
        return activityRepository
                .findById(uuid)
                .filter(a -> a.getDeletedTime() == null)
                .orElseThrow(() -> new IllegalArgumentException("活动不存在"));
    }
}
