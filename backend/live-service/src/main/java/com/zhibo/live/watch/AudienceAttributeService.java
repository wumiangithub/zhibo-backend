package com.zhibo.live.watch;

import com.zhibo.activity.ActivityAudienceEntity;
import com.zhibo.activity.ActivityAudienceRepository;
import com.zhibo.activity.ActivityEntity;
import com.zhibo.activity.ActivityRepository;
import com.zhibo.common.UuidIds;
import com.zhibo.live.watch.dto.AttributeAudienceRequest;
import com.zhibo.live.watch.dto.AttributeAudienceResponse;
import com.zhibo.viewer.ViewerRepository;
import java.time.LocalDateTime;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 观看分享链邀请归因（D77 / D97）。
 * 被邀请观众 id 由 Controller 从观众 JWT 传入；{@code inviterKind=employee} 写本场员工邀请人；
 * {@code audience} 观众互邀本站不落库（分支保留）。
 */
@Service
@RequiredArgsConstructor
public class AudienceAttributeService {

    static final String KIND_EMPLOYEE = "employee";
    static final String KIND_AUDIENCE = "audience";

    private final ActivityRepository activityRepository;
    private final ActivityAudienceRepository audienceRepository;
    private final ViewerRepository viewerRepository;

    @Transactional
    public AttributeAudienceResponse attribute(
            String activityId, long viewerId, AttributeAudienceRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("请求体不能为空");
        }
        if (viewerId <= 0) {
            throw new IllegalArgumentException("viewerId 无效");
        }
        ActivityEntity activity = requireActivity(activityId);
        if (!viewerRepository.existsById(viewerId)) {
            throw new IllegalArgumentException("观众不存在");
        }
        Long inviterId = request.getInviterId();
        if (inviterId == null || inviterId <= 0) {
            throw new IllegalArgumentException("inviterId 无效");
        }

        String kind = normalizeKind(request.getInviterKind());
        if (KIND_AUDIENCE.equals(kind)) {
            // 产品：不记录观众邀请；栏空。分支必须保留，勿删成「只认 employee」。
            throw new IllegalArgumentException("不记录观众邀请");
        }
        if (!KIND_EMPLOYEE.equals(kind)) {
            throw new IllegalArgumentException("inviterKind 须为 employee 或 audience");
        }
        return attributeEmployee(activity, viewerId, inviterId);
    }

    /** 员工邀请：空则填 employee_inviter_*（先导入后邀请可补填）。 */
    private AttributeAudienceResponse attributeEmployee(
            ActivityEntity activity, long viewerId, Long inviterId) {
        LocalDateTime now = LocalDateTime.now();
        ActivityAudienceEntity row = audienceRepository
                .findByActivityIdAndViewerId(activity.getId(), viewerId)
                .orElseGet(() -> {
                    ActivityAudienceEntity created = new ActivityAudienceEntity();
                    created.setActivityId(activity.getId());
                    created.setViewerId(viewerId);
                    created.setEntitlementStatus(1);
                    return created;
                });

        if (row.getEmployeeInviterId() == null) {
            row.setEmployeeInviterId(inviterId);
            row.setEmployeeInviteTime(now);
        }

        audienceRepository.save(row);
        return new AttributeAudienceResponse(row.getId(), row.getViewerId());
    }

    /** 缺省 / 空串 → employee。 */
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
