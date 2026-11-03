package com.zhibo.admin.course.dto;

import java.util.ArrayList;
import java.util.List;
import lombok.Data;

@Data
public class CourseAnalyticsUserLessonListResponse {

    private long viewerId;
    private String nickname;
    private String avatarUrl;
    private long completedLessonCount;
    private double lessonCompletionRate;
    private long totalLessonCount;
    private long total;
    private int page;
    private int pageSize;
    private List<CourseAnalyticsUserLessonItem> list = new ArrayList<>();
}
