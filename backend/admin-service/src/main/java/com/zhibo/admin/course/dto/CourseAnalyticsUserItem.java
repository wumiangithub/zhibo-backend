package com.zhibo.admin.course.dto;

import lombok.Data;

@Data
public class CourseAnalyticsUserItem {

    private String username;
    private String nickname;
    private String avatarUrl;
    /** 0 未开始 / 1 进行中 / 2 已完成 */
    private int participateStatus;
    private long completedLessonCount;
    /** 百分数一位小数 */
    private double lessonCompletionRate;
    private long learnDurationSec;
    /** valid / expired / invalid */
    private String entitlementDisplay;
}
