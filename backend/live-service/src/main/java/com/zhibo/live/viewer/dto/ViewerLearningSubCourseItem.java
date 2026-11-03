package com.zhibo.live.viewer.dto;

import lombok.Data;

/** 课程详情 · 子课程卡片。 */
@Data
public class ViewerLearningSubCourseItem {

    private Long id;
    private String title;
    private String coverUrl;
    /** 该子课程下小节数（含章内小节）。 */
    private int lessonCount;
}
