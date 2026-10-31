package com.zhibo.live.watch;

import com.zhibo.activity.RoomCommentEntity;
import com.zhibo.activity.RoomCommentRepository;
import com.zhibo.activity.ActivityEntity;
import com.zhibo.activity.ActivityRepository;
import com.zhibo.activity.AttendanceTimes;
import com.zhibo.common.UuidIds;
import com.zhibo.live.watch.dto.DeleteCommentRequest;
import com.zhibo.live.watch.dto.ReportCommentRequest;
import com.zhibo.live.watch.dto.ReportCommentResponse;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class WatchCommentService {

    private final ActivityRepository activityRepository;
    private final RoomCommentRepository commentRepository;

    @Transactional
    public ReportCommentResponse report(String activityId, ReportCommentRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("请求体不能为空");
        }
        ActivityEntity activity = requireActivity(activityId);
        String content = request.getContent() == null ? "" : request.getContent().trim();
        if (content.isEmpty()) {
            throw new IllegalArgumentException("content 不能为空");
        }
        if (content.length() > 500) {
            throw new IllegalArgumentException("content 最长 500");
        }
        Long viewerId = request.getViewerId();
        String guestId = blankToNull(request.getGuestId(), 64);
        if (viewerId == null && guestId == null) {
            throw new IllegalArgumentException("guestId 与 viewerId 至少一个");
        }
        String providerMsgId = blankToNull(request.getProviderMsgId(), 64);
        if (providerMsgId != null) {
            var existing = commentRepository.findByActivityIdAndProviderMsgId(activity.getId(), providerMsgId);
            if (existing.isPresent()) {
                // 他端可能先以 guestId 幂等入库；本人回声补上 viewerId，评论次数才有数
                RoomCommentEntity row = existing.get();
                if (row.getViewerId() == null && viewerId != null) {
                    row.setViewerId(viewerId);
                    commentRepository.save(row);
                }
                return new ReportCommentResponse(row.getId());
            }
        }

        LocalDateTime commentTime = request.getCommentTime() == null || request.getCommentTime().isBlank()
                ? LocalDateTime.now()
                : AttendanceTimes.parse(request.getCommentTime(), "commentTime");

        RoomCommentEntity entity = new RoomCommentEntity();
        entity.setActivityId(activity.getId());
        entity.setViewerId(viewerId);
        entity.setGuestId(guestId);
        entity.setContent(content);
        entity.setNickname(blankToNull(request.getNickname(), 64));
        entity.setRoleName(request.getRoleName());
        entity.setProviderMsgId(providerMsgId);
        entity.setCommentTime(commentTime);
        try {
            commentRepository.save(entity);
        } catch (DataIntegrityViolationException dup) {
            if (providerMsgId != null) {
                return commentRepository
                        .findByActivityIdAndProviderMsgId(activity.getId(), providerMsgId)
                        .map(c -> new ReportCommentResponse(c.getId()))
                        .orElseThrow(() -> dup);
            }
            throw dup;
        }
        return new ReportCommentResponse(entity.getId());
    }

    @Transactional
    public ReportCommentResponse softDelete(String activityId, DeleteCommentRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("请求体不能为空");
        }
        ActivityEntity activity = requireActivity(activityId);
        RoomCommentEntity entity = null;
        if (request.getCommentId() != null) {
            entity = commentRepository
                    .findById(request.getCommentId())
                    .filter(c -> c.getActivityId().equals(activity.getId()))
                    .orElse(null);
        } else if (request.getProviderMsgId() != null && !request.getProviderMsgId().isBlank()) {
            entity = commentRepository
                    .findByActivityIdAndProviderMsgId(activity.getId(), request.getProviderMsgId().trim())
                    .orElse(null);
        } else {
            throw new IllegalArgumentException("providerMsgId 或 commentId 必填其一");
        }
        if (entity == null) {
            throw new IllegalArgumentException("评论不存在");
        }
        if (entity.getDeletedTime() == null) {
            entity.setDeletedTime(LocalDateTime.now());
            commentRepository.save(entity);
        }
        return new ReportCommentResponse(entity.getId());
    }

    private ActivityEntity requireActivity(String activityId) {
        String uuid = UuidIds.require(activityId, "活动 id 无效", "活动 id 须为本站 UUID");
        return activityRepository
                .findById(uuid)
                .filter(a -> a.getDeletedTime() == null)
                .orElseThrow(() -> new IllegalArgumentException("活动不存在"));
    }

    private static String blankToNull(String raw, int max) {
        if (raw == null) {
            return null;
        }
        String t = raw.trim();
        if (t.isEmpty()) {
            return null;
        }
        if (t.length() > max) {
            throw new IllegalArgumentException("字段过长");
        }
        return t;
    }
}
