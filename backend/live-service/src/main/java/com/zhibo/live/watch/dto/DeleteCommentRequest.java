package com.zhibo.live.watch.dto;

import lombok.Data;

@Data
public class DeleteCommentRequest {

    private String msgId;
    private Long commentId;
}
