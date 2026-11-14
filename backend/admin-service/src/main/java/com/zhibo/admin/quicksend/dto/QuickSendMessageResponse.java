package com.zhibo.admin.quicksend.dto;

import lombok.Data;

@Data
public class QuickSendMessageResponse {
    private String id;
    private String groupId;
    private String content;
    private int sortOrder;
    private String createdTime;
    private String updatedTime;
}
