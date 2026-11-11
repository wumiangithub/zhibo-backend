package com.zhibo.live.watch;

import com.zhibo.activity.RoomCommentEntity;
import com.zhibo.activity.RoomCommentRepository;
import com.zhibo.activity.RoomCommentType;
import com.zhibo.activity.ActivityEntity;
import com.zhibo.activity.ActivityRepository;
import com.zhibo.activity.AttendanceTimes;
import com.zhibo.common.UuidIds;
import com.zhibo.live.watch.dto.DeleteCommentRequest;
import com.zhibo.live.watch.dto.ReportCommentRequest;
import com.zhibo.live.watch.dto.ReportCommentResponse;
import com.zhibo.live.watch.dto.UpsertQuestionAnswerRequest;
import java.time.LocalDateTime;
import java.util.Objects;
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
    public ReportCommentResponse report(String activityId, long viewerId, ReportCommentRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("请求体不能为空");
        }
        if (viewerId <= 0) {
            throw new IllegalArgumentException("uid 无效");
        }
        ActivityEntity activity = requireActivity(activityId);
        String content = request.getContent() == null ? "" : request.getContent().trim();
        if (content.isEmpty()) {
            throw new IllegalArgumentException("content 不能为空");
        }
        if (content.length() > 500) {
            throw new IllegalArgumentException("content 最长 500");
        }
        int commentType = resolveCommentType(request.getCommentType());
        String questionId = blankToNull(request.getQuestionId(), 64);
        String msgId = blankToNull(request.getMsgId(), 64);
        if (commentType == RoomCommentType.QUESTION && questionId == null) {
            throw new IllegalArgumentException("提问须传 questionId");
        }

        if (questionId != null) {
            var byQuestion = commentRepository.findByActivityIdAndQuestionId(
                    activity.getId(), questionId);
            if (byQuestion.isPresent()) {
                return touchExisting(byQuestion.get(), activity, viewerId, msgId);
            }
        } else if (msgId != null) {
            var byMsg = commentRepository.findByActivityIdAndMsgId(activity.getId(), msgId);
            if (byMsg.isPresent()) {
                return touchExisting(byMsg.get(), activity, viewerId, null);
            }
        }

        LocalDateTime commentTime = request.getCommentTime() == null || request.getCommentTime().isBlank()
                ? LocalDateTime.now()
                : AttendanceTimes.parse(request.getCommentTime(), "commentTime");

        RoomCommentEntity entity = new RoomCommentEntity();
        entity.setActivityId(activity.getId());
        copyCloudContext(entity, activity);
        entity.setUid(viewerId);
        entity.setContent(content);
        entity.setNickname(blankToNull(request.getNickname(), 64));
        entity.setRoleName(request.getRoleName());
        entity.setCommentType(commentType);
        entity.setQuestionId(questionId);
        entity.setMsgId(msgId);
        entity.setCommentTime(commentTime);
        try {
            commentRepository.save(entity);
        } catch (DataIntegrityViolationException dup) {
            if (questionId != null) {
                return commentRepository
                        .findByActivityIdAndQuestionId(activity.getId(), questionId)
                        .map(c -> touchExisting(c, activity, viewerId, msgId))
                        .orElseThrow(() -> dup);
            }
            if (msgId != null) {
                return commentRepository
                        .findByActivityIdAndMsgId(activity.getId(), msgId)
                        .map(c -> touchExisting(c, activity, viewerId, null))
                        .orElseThrow(() -> dup);
            }
            throw dup;
        }
        return new ReportCommentResponse(entity.getId());
    }

    /**
     * 提问回复 upsert（D107）。按 {@code questionId}=云提问 id 找同一行写 answer_*；
     * 多观众同时 commit 重复 POST 幂等。行不存在时用提问正文补建（commentType=2）。
     * 可选 {@code msgId} 在行上为空时补写一次。
     */
    @Transactional
    public ReportCommentResponse upsertQuestionAnswer(
            String activityId, UpsertQuestionAnswerRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("请求体不能为空");
        }
        ActivityEntity activity = requireActivity(activityId);
        String questionId = blankToNull(request.getQuestionId(), 64);
        if (questionId == null) {
            throw new IllegalArgumentException("questionId 不能为空");
        }
        String msgId = blankToNull(request.getMsgId(), 64);
        String answerContent = request.getAnswerContent() == null ? "" : request.getAnswerContent().trim();
        if (answerContent.isEmpty()) {
            throw new IllegalArgumentException("answerContent 不能为空");
        }
        if (answerContent.length() > 500) {
            throw new IllegalArgumentException("answerContent 最长 500");
        }
        String answerNickname = blankToNull(request.getAnswerNickname(), 64);
        Integer answerRoleName = request.getAnswerRoleName();

        var existing = commentRepository.findByActivityIdAndQuestionId(
                activity.getId(), questionId);
        if (existing.isPresent()) {
            return applyAnswer(
                    existing.get(), activity, answerContent, answerNickname, answerRoleName, msgId);
        }

        String content = request.getContent() == null ? "" : request.getContent().trim();
        if (content.isEmpty()) {
            throw new IllegalArgumentException("提问不存在且缺少 content");
        }
        if (content.length() > 500) {
            throw new IllegalArgumentException("content 最长 500");
        }

        RoomCommentEntity entity = new RoomCommentEntity();
        entity.setActivityId(activity.getId());
        copyCloudContext(entity, activity);
        entity.setContent(content);
        entity.setNickname(blankToNull(request.getNickname(), 64));
        entity.setRoleName(request.getRoleName());
        entity.setCommentType(RoomCommentType.QUESTION);
        entity.setQuestionId(questionId);
        entity.setMsgId(msgId);
        entity.setAnswerContent(answerContent);
        entity.setAnswerNickname(answerNickname);
        entity.setAnswerRoleName(answerRoleName);
        entity.setCommentTime(LocalDateTime.now());
        try {
            commentRepository.save(entity);
            return new ReportCommentResponse(entity.getId());
        } catch (DataIntegrityViolationException dup) {
            return commentRepository
                    .findByActivityIdAndQuestionId(activity.getId(), questionId)
                    .map(row -> applyAnswer(
                            row, activity, answerContent, answerNickname, answerRoleName, msgId))
                    .orElseThrow(() -> dup);
        }
    }

    private ReportCommentResponse touchExisting(
            RoomCommentEntity row, ActivityEntity activity, long viewerId, String msgId) {
        boolean dirty = false;
        if (ensureCloudContext(row, activity)) {
            dirty = true;
        }
        if (row.getUid() == null) {
            row.setUid(viewerId);
            dirty = true;
        }
        if (msgId != null && blankToNull(row.getMsgId(), 64) == null) {
            row.setMsgId(msgId);
            dirty = true;
        }
        if (dirty) {
            commentRepository.save(row);
        }
        return new ReportCommentResponse(row.getId());
    }

    private ReportCommentResponse applyAnswer(
            RoomCommentEntity row,
            ActivityEntity activity,
            String answerContent,
            String answerNickname,
            Integer answerRoleName,
            String msgId) {
        boolean dirty = false;
        if (ensureCloudContext(row, activity)) {
            dirty = true;
        }
        if (row.getCommentType() == null || row.getCommentType() != RoomCommentType.QUESTION) {
            row.setCommentType(RoomCommentType.QUESTION);
            dirty = true;
        }
        if (msgId != null && blankToNull(row.getMsgId(), 64) == null) {
            row.setMsgId(msgId);
            dirty = true;
        }
        boolean sameAnswer =
                Objects.equals(blankToNull(row.getAnswerContent(), 500), answerContent)
                        && Objects.equals(blankToNull(row.getAnswerNickname(), 64), answerNickname)
                        && Objects.equals(row.getAnswerRoleName(), answerRoleName);
        if (!sameAnswer) {
            row.setAnswerContent(answerContent);
            row.setAnswerNickname(answerNickname);
            row.setAnswerRoleName(answerRoleName);
            dirty = true;
        }
        if (dirty) {
            commentRepository.save(row);
        }
        return new ReportCommentResponse(row.getId());
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
        } else if (request.getMsgId() != null && !request.getMsgId().isBlank()) {
            entity = commentRepository
                    .findByActivityIdAndMsgId(activity.getId(), request.getMsgId().trim())
                    .orElse(null);
        } else {
            throw new IllegalArgumentException("msgId 或 commentId 必填其一");
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

    /** 新行：从活动抄 provider + 云房间号。 */
    private static void copyCloudContext(RoomCommentEntity row, ActivityEntity activity) {
        row.setProvider(blankToNull(activity.getProvider(), 32));
        row.setRoomId(blankToNull(activity.getProviderRoomId(), 64));
    }

    /** 已有行：缺 provider/room_id 时补写一次。 */
    private static boolean ensureCloudContext(RoomCommentEntity row, ActivityEntity activity) {
        boolean dirty = false;
        if (blankToNull(row.getProvider(), 32) == null) {
            String p = blankToNull(activity.getProvider(), 32);
            if (p != null) {
                row.setProvider(p);
                dirty = true;
            }
        }
        if (blankToNull(row.getRoomId(), 64) == null) {
            String r = blankToNull(activity.getProviderRoomId(), 64);
            if (r != null) {
                row.setRoomId(r);
                dirty = true;
            }
        }
        return dirty;
    }

    private static int resolveCommentType(Integer commentType) {
        if (commentType == null) {
            return RoomCommentType.CHAT;
        }
        if (!RoomCommentType.isKnown(commentType)) {
            throw new IllegalArgumentException("commentType 须为 1 或 2");
        }
        return commentType;
    }

    private static String blankToNull(String value, int maxLen) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        return trimmed.length() <= maxLen ? trimmed : trimmed.substring(0, maxLen);
    }
}
