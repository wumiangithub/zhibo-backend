package com.zhibo.admin.attendance.dto;

import lombok.Data;

@Data
public class AttendeeItem {

    private String userId;
    private String nickname;
    private String avatarUrl;
    private String enterTime;
    private String leaveTime;
    private boolean online;
}
