package com.zhibo.live.watch.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ReportCommentRequest {

    @NotBlank(message = "content 不能为空")
    @Size(max = 500, message = "content 最长 500")
    private String content;

    private String guestId;
    private Long viewerId;

    @Size(max = 64)
    private String nickname;

    private Integer roleName;

    @Size(max = 64)
    private String providerMsgId;

    /** yyyy-MM-dd HH:mm:ss；缺省服务器当前时间 */
    private String commentTime;
}
