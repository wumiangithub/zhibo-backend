package com.zhibo.admin.course.dto;

import lombok.Data;

@Data
public class CourseAnalyticsLessonItem {

    private long lessonNodeId;
    private String title;
    private Integer contentKind;
    private long participateCount;
    /** 百分数一位小数，如 70.2 */
    private double participateRate;
    private long completeCount;
    private double completeRate;
}
