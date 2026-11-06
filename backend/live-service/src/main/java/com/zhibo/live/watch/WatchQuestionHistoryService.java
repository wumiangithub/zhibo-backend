package com.zhibo.live.watch;

import com.zhibo.activity.ActivityEntity;
import com.zhibo.activity.RoomCommentEntity;
import com.zhibo.activity.RoomCommentRepository;
import com.zhibo.activity.RoomCommentType;
import com.zhibo.live.watch.dto.WatchQuestionHistoryItem;
import com.zhibo.live.watch.dto.WatchQuestionHistoryResponse;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 本站问答历史：读 {@code room_comment}（comment_type=提问）。
 * 回放页直接用（无观看 SDK）；直播页在微吼 {@code vhall_get_live_history_question_msg} 空/失败后兜底。
 */
@Service
@RequiredArgsConstructor
public class WatchQuestionHistoryService {

    static final ZoneId SHANGHAI = ZoneId.of("Asia/Shanghai");
    /** 单场提问体量通常远小于聊天；超出截断，避免一次拖垮侧栏 */
    static final int MAX_LIST = 500;

    private final WatchService watchService;
    private final RoomCommentRepository commentRepository;

    @Transactional(readOnly = true)
    public WatchQuestionHistoryResponse list(String activityId) {
        ActivityEntity entity = watchService.requireActivity(activityId);
        watchService.requireReadyForWatch(entity);

        List<RoomCommentEntity> rows =
                commentRepository.findByActivityIdAndCommentTypeAndDeletedTimeIsNullOrderByCommentTimeAsc(
                        entity.getId(), RoomCommentType.QUESTION);

        WatchQuestionHistoryResponse response = new WatchQuestionHistoryResponse();
        response.setTotal(rows.size());
        int limit = Math.min(rows.size(), MAX_LIST);
        for (int i = 0; i < limit; i++) {
            WatchQuestionHistoryItem item = toItem(rows.get(i));
            if (item.getMsg() == null || item.getMsg().isBlank()) {
                continue;
            }
            if (item.getSenderName() == null || item.getSenderName().isBlank()) {
                continue;
            }
            response.getList().add(item);
        }
        return response;
    }

    private static WatchQuestionHistoryItem toItem(RoomCommentEntity c) {
        WatchQuestionHistoryItem item = new WatchQuestionHistoryItem();
        String questionId = blankToNull(c.getQuestionId());
        item.setQuestionId(questionId);
        if (c.getViewerId() != null && c.getViewerId() > 0) {
            item.setSenderId(Long.toString(c.getViewerId()));
        }
        String nick = blankToNull(c.getNickname());
        item.setSenderName(nick != null ? nick : "观众");
        item.setMsg(c.getContent() == null ? "" : c.getContent().trim());
        item.setMsgId(blankToNull(c.getMsgId()));
        item.setRoleName(c.getRoleName());
        item.setTimestamp(toEpochMillis(c.getCommentTime()));
        String answer = blankToNull(c.getAnswerContent());
        if (answer != null) {
            item.setAnswerContent(answer);
            item.setAnswerNickname(blankToNull(c.getAnswerNickname()));
            item.setAnswerRoleName(c.getAnswerRoleName());
        }
        return item;
    }

    private static Long toEpochMillis(LocalDateTime time) {
        if (time == null) {
            return System.currentTimeMillis();
        }
        return time.atZone(SHANGHAI).toInstant().toEpochMilli();
    }

    private static String blankToNull(String raw) {
        if (raw == null) {
            return null;
        }
        String t = raw.trim();
        return t.isEmpty() ? null : t;
    }
}
