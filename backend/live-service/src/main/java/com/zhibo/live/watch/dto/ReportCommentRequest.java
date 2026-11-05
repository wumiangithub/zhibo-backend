package com.zhibo.live.watch.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 观看页评论上报。观众 id 只从 Authorization 观众 JWT 取；不接受 guestId / viewerId。
 */
@Data
public class ReportCommentRequest {

    @NotBlank(message = "content 不能为空")
    @Size(max = 500, message = "content 最长 500")
    private String content;

    @Size(max = 64)
    private String nickname;

    private Integer roleName;

    /**
     * 1 聊天 / 2 提问。省略按聊天。
     */
    private Integer commentType;

    /**
     * 云消息 id（如微吼 msg_id）。聊天观看端通常不传；提问可在 create 后补传。
     * 勿把提问 data.id 写进本字段。
     */
    @Size(max = 64)
    private String msgId;

    /**
     * 云提问 id（如微吼 data.id）。提问上报必传；聊天不传。
     */
    @Size(max = 64)
    private String questionId;

    /** yyyy-MM-dd HH:mm:ss；缺省服务器当前时间 */
    private String commentTime;
}
