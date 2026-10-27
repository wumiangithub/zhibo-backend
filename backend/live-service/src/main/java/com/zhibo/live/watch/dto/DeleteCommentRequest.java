package com.zhibo.live.watch.dto;

import lombok.Data;

@Data
public class DeleteCommentRequest {

    private String providerMsgId;
    private Long commentId;
}
