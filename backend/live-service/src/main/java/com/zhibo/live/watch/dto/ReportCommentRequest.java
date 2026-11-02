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

    @Size(max = 64)
    private String providerMsgId;

    /** yyyy-MM-dd HH:mm:ss；缺省服务器当前时间 */
    private String commentTime;
}
