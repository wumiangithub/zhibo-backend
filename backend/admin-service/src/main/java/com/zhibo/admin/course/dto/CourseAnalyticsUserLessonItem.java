package com.zhibo.admin.course.dto;

import lombok.Data;

@Data
public class CourseAnalyticsUserLessonItem {

    private long lessonNodeId;
    private String title;
    private Integer contentKind;
    /** 0 未开始 / 1 进行中 / 2 已完成 */
    private int participateStatus;
    private long learnDurationSec;
    /** 仅直播/视频有值；其它 null */
    private Integer progressBarSec;
}
