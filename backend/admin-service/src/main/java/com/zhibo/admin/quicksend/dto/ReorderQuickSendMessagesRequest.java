package com.zhibo.admin.quicksend.dto;

import lombok.Data;

import java.util.List;

@Data
public class ReorderQuickSendMessagesRequest {
    private String groupId;
    private List<String> ids;
}
