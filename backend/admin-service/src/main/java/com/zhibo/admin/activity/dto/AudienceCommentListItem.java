package com.zhibo.admin.activity.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AudienceCommentListItem {

    private Long id;
    private String username;
    private String guestId;
    private String nickname;
    private String content;
    /** 1 聊天 / 2 提问。 */
    private Integer commentType;
    private String commentTime;
    private boolean deleted;
}
