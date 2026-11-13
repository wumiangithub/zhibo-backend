package com.zhibo.admin.attendance.dto;

import lombok.Data;

@Data
public class AttendeeItem {

    /** 邮箱本地部分：观众 username / e{员工id} / z{员工id}（库列 username）。 */
    private String username;

    private String nickname;
    private String avatarUrl;
    private String enterTime;
    private String leaveTime;
    private boolean online;
}
