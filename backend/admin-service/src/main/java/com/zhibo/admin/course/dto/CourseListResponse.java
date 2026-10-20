package com.zhibo.admin.course.dto;

import lombok.Data;

import java.util.List;

@Data
public class CourseListResponse {

    private int page;
    private int pageSize;
    private long total;
    private List<CourseSummary> list;
}
