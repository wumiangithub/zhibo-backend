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

/** 仅员工邀请归因；不记录观众邀请（D77）。先导入后员工邀请可补填。 */
@Service
@RequiredArgsConstructor
public class AudienceAttributeService {

    private final ActivityRepository activityRepository;
    private final ActivityAudienceRepository audienceRepository;
    private final ViewerRepository viewerRepository;

    @Transactional
    public AttributeAudienceResponse attribute(String activityId, AttributeAudienceRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("请求体不能为空");
        }
        ActivityEntity activity = requireActivity(activityId);
        Long viewerId = request.getViewerId();
        if (viewerId == null || viewerId <= 0) {
            throw new IllegalArgumentException("viewerId 无效");
        }
        if (!viewerRepository.existsById(viewerId)) {
            throw new IllegalArgumentException("观众不存在");
        }
        Long inviterId = request.getInviterId();
        if (inviterId == null || inviterId <= 0) {
            throw new IllegalArgumentException("inviterId 无效");
        }
        String kind = request.getInviterKind() == null
                ? "employee"
                : request.getInviterKind().trim().toLowerCase(Locale.ROOT);
        if (kind.isEmpty()) {
            kind = "employee";
        }
        if ("audience".equals(kind)) {
            throw new IllegalArgumentException("不记录观众邀请");
        }
        if (!"employee".equals(kind)) {
            throw new IllegalArgumentException("inviterKind 须为 employee");
        }

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

        // 导入只写 importer_*；邀请字段空时补填员工邀请（先导入后邀请）
        if (row.getEmployeeInviterId() == null) {
            row.setEmployeeInviterId(inviterId);
            row.setEmployeeInviteTime(now);
        }

        audienceRepository.save(row);
        return new AttributeAudienceResponse(row.getId(), row.getViewerId());
    }

    private ActivityEntity requireActivity(String activityId) {
        String uuid = UuidIds.require(activityId, "活动 id 无效", "活动 id 须为本站 UUID");
        return activityRepository
                .findById(uuid)
                .filter(a -> a.getDeletedTime() == null)
                .orElseThrow(() -> new IllegalArgumentException("活动不存在"));
    }
}
