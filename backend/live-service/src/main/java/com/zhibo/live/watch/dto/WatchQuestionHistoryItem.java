package com.zhibo.live.watch.dto;

import lombok.Data;

/** {@code GET /api/v1/watch/question-history/{id}} 单条（本站 room_comment 提问行）。 */
@Data
public class WatchQuestionHistoryItem {

    /** 云提问 id（room_comment.question_id）；列表主键，对齐直播侧 ChatMessage.msgId */
    private String questionId;
    /** 发言观众 id；可空（补建提问行可能无 viewer_id） */
    private String senderId;
    private String senderName;
    private String msg;
    /** 云消息 id（room_comment.msg_id）；可空 */
    private String msgId;
    /** 1 主持 / 2 观众 / 3 助理 / 4 嘉宾；可空 */
    private Integer roleName;
    /** 毫秒 */
    private Long timestamp;
    /** 主持人/嘉宾回复正文；无可空 */
    private String answerContent;
    private String answerNickname;
    /** 回复人微吼角色数字；可空 */
    private Integer answerRoleName;
}
