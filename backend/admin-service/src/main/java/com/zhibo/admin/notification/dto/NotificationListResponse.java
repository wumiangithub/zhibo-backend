package com.zhibo.admin.notification.dto;

import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
public class NotificationListResponse {

    private int page;
    private int pageSize;
    private long total;
    private List<NotificationItemResponse> list = new ArrayList<>();
}
