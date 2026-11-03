package com.zhibo.admin.course.dto;

import java.util.ArrayList;
import java.util.List;
import lombok.Data;

@Data
public class CourseAnalyticsLessonListResponse {

    private long total;
    private int page;
    private int pageSize;
    private long validStudentCount;
    private List<CourseAnalyticsLessonItem> list = new ArrayList<>();
}
