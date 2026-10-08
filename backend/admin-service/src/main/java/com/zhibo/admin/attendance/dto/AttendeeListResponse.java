package com.zhibo.admin.attendance.dto;

import lombok.Data;

import java.util.List;

@Data
public class AttendeeListResponse {

    private Integer activityState;
    private long total;
    private int page;
    private int pageSize;
    private List<AttendeeItem> list;
}
