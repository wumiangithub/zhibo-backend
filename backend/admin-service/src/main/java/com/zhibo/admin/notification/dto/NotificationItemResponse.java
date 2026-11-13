package com.zhibo.admin.notification.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class NotificationItemResponse {

    private String id;
    private Integer type;
    private String category;
    private String title;
    private String body;
    private Integer auditResult;
    private Integer refType;
    private String refId;
    private Boolean unread;
    private String createdTime;
}
