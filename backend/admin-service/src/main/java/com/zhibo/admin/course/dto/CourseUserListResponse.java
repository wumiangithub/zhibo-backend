package com.zhibo.admin.course.dto;

import java.util.ArrayList;
import java.util.List;
import lombok.Data;

@Data
public class CourseUserListResponse {

    private int page;
    private int pageSize;
    private long total;
    private List<CourseUserListItem> list = new ArrayList<>();
}
