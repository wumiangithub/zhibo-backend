package com.zhibo.admin.course.dto;

import java.util.ArrayList;
import java.util.List;
import lombok.Data;

@Data
public class CourseAnalyticsUserListResponse {

    private long total;
    private int page;
    private int pageSize;
    private List<CourseAnalyticsUserItem> list = new ArrayList<>();
}
