package com.zhibo.admin.activity.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AudienceCommentListItem {

    private Long id;
    private Long viewerId;
    private String guestId;
    private String nickname;
    private String content;
    private String commentTime;
    private boolean deleted;
}
