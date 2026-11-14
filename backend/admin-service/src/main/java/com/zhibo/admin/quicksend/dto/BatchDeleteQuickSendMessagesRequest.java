package com.zhibo.admin.quicksend.dto;

import lombok.Data;

import java.util.List;

@Data
public class BatchDeleteQuickSendMessagesRequest {
    private List<String> ids;
}
