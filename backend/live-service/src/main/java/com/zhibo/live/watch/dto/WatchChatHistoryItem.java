package com.zhibo.live.watch.dto;

import lombok.Data;

/** {@code GET /api/v1/watch/chat-history/{id}} 单条。 */
@Data
public class WatchChatHistoryItem {

    private String senderId;
    private String senderName;
    private String msg;
    private String msgId;
    private String avatar;
    /** 1 主持 / 2 观众 / 3 助理 / 4 嘉宾 */
    private Integer roleName;
    /** 毫秒 */
    private Long timestamp;
}
