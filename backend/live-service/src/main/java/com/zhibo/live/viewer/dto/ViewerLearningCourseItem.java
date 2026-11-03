package com.zhibo.live.viewer.dto;

import lombok.Data;

/** 小程序「我的学习 · 课程」列表项。 */
@Data
public class ViewerLearningCourseItem {

    /** 本站课程 UUID。 */
    private String id;

    private String title;

    private String coverUrl;

    /** 未软删子课程数。 */
    private int subCourseCount;

    /** 小节数（读 course.content_count）。 */
    private int lessonCount;

    /** 本学员获得权益时间，yyyy-MM-dd HH:mm:ss（course_user.created_time）。 */
    private String acquiredTime;
}
