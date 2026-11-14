package com.zhibo.live.watch;

import com.zhibo.activity.ActivityEntity;
import com.zhibo.activity.RoomCommentEntity;
import com.zhibo.activity.RoomCommentRepository;
import com.zhibo.activity.RoomCommentType;
import com.zhibo.activity.RoomSystemContent;
import com.zhibo.live.watch.dto.WatchSystemMessageHistoryItem;
import com.zhibo.live.watch.dto.WatchSystemMessageHistoryResponse;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 观看端系统消息历史（D127）。 */
@Service
@RequiredArgsConstructor
public class WatchSystemMessageService {

    private static final ZoneId SHANGHAI = ZoneId.of("Asia/Shanghai");
    private static final DateTimeFormatter COMMENT_TIME =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    static final int MAX_HISTORY = 500;

    private final WatchService watchService;
    private final RoomCommentRepository commentRepository;

    @Transactional(readOnly = true)
    public WatchSystemMessageHistoryResponse history(String activityId) {
        ActivityEntity entity = watchService.requireActivity(activityId);
        watchService.requireReadyForWatch(entity);
        List<RoomCommentEntity> rows =
                commentRepository.findByActivityIdAndCommentTypeAndDeletedTimeIsNullOrderByCommentTimeAsc(
                        entity.getId(), RoomCommentType.SYSTEM);
        WatchSystemMessageHistoryResponse response = new WatchSystemMessageHistoryResponse();
        int limit = Math.min(rows.size(), MAX_HISTORY);
        Long pinnedId = null;
        for (int i = 0; i < limit; i++) {
            WatchSystemMessageHistoryItem item = toHistoryItem(rows.get(i));
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

    private static WatchSystemMessageHistoryItem toHistoryItem(RoomCommentEntity c) {
        RoomSystemContent parsed = RoomSystemContent.parse(c.getContent());
        if (parsed == null) {
            return null;
        }
        WatchSystemMessageHistoryItem item = new WatchSystemMessageHistoryItem();
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
}
