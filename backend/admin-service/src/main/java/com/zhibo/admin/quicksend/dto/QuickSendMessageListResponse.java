package com.zhibo.admin.quicksend.dto;

import lombok.Data;

import java.util.List;

@Data
public class QuickSendMessageListResponse {
    private long total;
    private int page;
    private int pageSize;
    private List<QuickSendMessageResponse> list;
}
