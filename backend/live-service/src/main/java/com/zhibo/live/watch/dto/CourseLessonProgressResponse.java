package com.zhibo.live.watch.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class CourseLessonProgressResponse {

    private long lessonNodeId;
    /** 0 未开始 / 1 进行中 / 2 已完成 */
    private int participateStatus;
    private long learnDurationSec;
    private Integer progressBarSec;
    private int progressPercent;
    private boolean completed;
}
