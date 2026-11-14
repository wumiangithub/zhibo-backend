package com.zhibo.admin.console;

import com.zhibo.activity.ActivityEntity;
import com.zhibo.activity.ActivityMemberEntity;
import com.zhibo.activity.ActivityMemberId;
import com.zhibo.activity.ActivityMemberRepository;
import com.zhibo.activity.ActivityRepository;
import com.zhibo.activity.RoomCommentEntity;
import com.zhibo.activity.RoomCommentRepository;
import com.zhibo.activity.RoomCommentType;
import com.zhibo.activity.RoomSystemContent;
import com.zhibo.admin.auth.SysUserAccountEntity;
import com.zhibo.admin.auth.SysUserAccountRepository;
import com.zhibo.admin.console.dto.ConsoleSendSystemMessageRequest;
import com.zhibo.admin.console.dto.ConsoleSendSystemMessageResponse;
import com.zhibo.admin.console.dto.ConsoleSystemMessageHistoryItem;
import com.zhibo.admin.console.dto.ConsoleSystemMessageHistoryResponse;
import com.zhibo.admin.console.dto.ConsoleUnpinSystemMessageRequest;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 中控台系统消息落库 + 历史（D127）。 */
@Service
@RequiredArgsConstructor
public class ConsoleSystemMessageService {

    private static final ZoneId SHANGHAI = ZoneId.of("Asia/Shanghai");
    private static final DateTimeFormatter COMMENT_TIME =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    static final int MAX_HISTORY = 500;
    static final int MAX_TEXT = 200;

    private final ActivityRepository activityRepository;
    private final ActivityMemberRepository memberRepository;
    private final SysUserAccountRepository userRepository;
    private final RoomCommentRepository commentRepository;
    private final Clock clock;

    @Transactional
    public ConsoleSendSystemMessageResponse send(
            String activityId, long employeeId, ConsoleSendSystemMessageRequest request) {
        if (employeeId <= 0) {
            throw new IllegalArgumentException("请先登录");
        }
        ActivityEntity activity = requireActivity(activityId);
        String text = normalizeText(request == null ? null : request.getText());
        boolean pin = request != null && Boolean.TRUE.equals(request.getPin());

        SysUserAccountEntity employee = userRepository
                .findById(employeeId)
                .orElseThrow(() -> new IllegalArgumentException("当前员工不存在"));
        ActivityMemberEntity member = memberRepository
                .findById(new ActivityMemberId(activity.getId(), employeeId))
                .orElse(null);
        String nickname = ConsoleService.resolveConsoleNickname(null, member, employee);
        LocalDateTime now = LocalDateTime.now(clock.withZone(SHANGHAI));

        if (pin) {
            clearPins(activity.getId());
        }

        RoomSystemContent snapshot = new RoomSystemContent(text, pin);
        RoomCommentEntity comment = new RoomCommentEntity();
        comment.setActivityId(activity.getId());
        comment.setProvider(activity.getProvider());
        comment.setRoomId(trimToNull(activity.getProviderRoomId()));
        comment.setContent(snapshot.toJson());
        comment.setNickname(nickname);
        comment.setCommentType(RoomCommentType.SYSTEM);
        comment.setCommentTime(now);
        commentRepository.save(comment);

        ConsoleSendSystemMessageResponse response = new ConsoleSendSystemMessageResponse();
        response.setCommentId(comment.getId());
        response.setText(text);
        response.setPin(pin);
        response.setNickname(nickname);
        response.setCommentTime(COMMENT_TIME.format(now));
        return response;
    }

    @Transactional
    public ConsoleSendSystemMessageResponse unpin(
            String activityId, long employeeId, ConsoleUnpinSystemMessageRequest request) {
        if (employeeId <= 0) {
            throw new IllegalArgumentException("请先登录");
        }
        if (request == null || request.getCommentId() == null || request.getCommentId() <= 0) {
            throw new IllegalArgumentException("commentId 无效");
        }
        ActivityEntity activity = requireActivity(activityId);
        RoomCommentEntity comment = commentRepository
                .findById(request.getCommentId())
                .orElseThrow(() -> new IllegalArgumentException("系统消息不存在"));
        if (!activity.getId().equals(comment.getActivityId())
                || comment.getDeletedTime() != null
                || comment.getCommentType() == null
                || comment.getCommentType() != RoomCommentType.SYSTEM) {
            throw new IllegalArgumentException("系统消息不存在");
        }
        RoomSystemContent parsed = RoomSystemContent.parse(comment.getContent());
        if (parsed == null) {
            throw new IllegalArgumentException("系统消息内容无效");
        }
        comment.setContent(parsed.withPin(false).toJson());
        commentRepository.save(comment);

        ConsoleSendSystemMessageResponse response = new ConsoleSendSystemMessageResponse();
        response.setCommentId(comment.getId());
        response.setText(parsed.text());
        response.setPin(false);
        response.setNickname(comment.getNickname());
        if (comment.getCommentTime() != null) {
            response.setCommentTime(COMMENT_TIME.format(comment.getCommentTime()));
        }
        return response;
    }

    @Transactional(readOnly = true)
    public ConsoleSystemMessageHistoryResponse history(String activityId) {
        ActivityEntity activity = requireActivity(activityId);
        List<RoomCommentEntity> rows =
                commentRepository.findByActivityIdAndCommentTypeAndDeletedTimeIsNullOrderByCommentTimeAsc(
                        activity.getId(), RoomCommentType.SYSTEM);

        ConsoleSystemMessageHistoryResponse response = new ConsoleSystemMessageHistoryResponse();
        int limit = Math.min(rows.size(), MAX_HISTORY);
        Long pinnedId = null;
        for (int i = 0; i < limit; i++) {
            ConsoleSystemMessageHistoryItem item = toHistoryItem(rows.get(i));
            if (item == null) {
                continue;
            }
            response.getList().add(item);
            if (item.isPin()) {
                pinnedId = item.getCommentId();
            }
        }
        response.setPinnedCommentId(pinnedId);
        return response;
    }

    private void clearPins(String activityId) {
        List<RoomCommentEntity> rows =
                commentRepository.findByActivityIdAndCommentTypeAndDeletedTimeIsNullOrderByCommentTimeAsc(
                        activityId, RoomCommentType.SYSTEM);
        for (RoomCommentEntity row : rows) {
            RoomSystemContent parsed = RoomSystemContent.parse(row.getContent());
            if (parsed == null || !parsed.pin()) {
                continue;
            }
            row.setContent(parsed.withPin(false).toJson());
            commentRepository.save(row);
        }
    }

    static ConsoleSystemMessageHistoryItem toHistoryItem(RoomCommentEntity c) {
        RoomSystemContent parsed = RoomSystemContent.parse(c.getContent());
        if (parsed == null) {
            return null;
        }
        ConsoleSystemMessageHistoryItem item = new ConsoleSystemMessageHistoryItem();
        item.setCommentId(c.getId());
        item.setText(parsed.text());
        item.setPin(parsed.pin());
        item.setNickname(c.getNickname());
        if (c.getCommentTime() != null) {
            item.setCommentTime(COMMENT_TIME.format(c.getCommentTime()));
            item.setTimestamp(c.getCommentTime().atZone(SHANGHAI).toInstant().toEpochMilli());
        }
        return item;
    }

    private static String normalizeText(String raw) {
        String text = raw == null ? "" : raw.trim();
        if (text.isEmpty()) {
            throw new IllegalArgumentException("请输入内容");
        }
        if (text.length() > MAX_TEXT) {
            throw new IllegalArgumentException("内容不能超过 " + MAX_TEXT + " 字");
        }
        return text;
    }

    private ActivityEntity requireActivity(String activityId) {
        String id = activityId == null ? "" : activityId.trim();
        if (id.isEmpty()) {
            throw new IllegalArgumentException("活动不存在");
        }
        ActivityEntity activity = activityRepository
                .findById(id)
                .orElseThrow(() -> new IllegalArgumentException("活动不存在"));
        if (activity.getDeletedTime() != null) {
            throw new IllegalArgumentException("活动不存在");
        }
        return activity;
    }

    private static String trimToNull(String raw) {
        if (raw == null) {
            return null;
        }
        String t = raw.trim();
        return t.isEmpty() ? null : t;
    }
}
