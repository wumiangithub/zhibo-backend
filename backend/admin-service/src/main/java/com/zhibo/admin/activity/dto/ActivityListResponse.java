package com.zhibo.admin.activity.dto;

import lombok.Data;

import java.util.List;

@Data
public class ActivityListResponse {
    private long total;
    private List<ActivitySummary> list;
}
