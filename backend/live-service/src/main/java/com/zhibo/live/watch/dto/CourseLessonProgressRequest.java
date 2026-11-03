package com.zhibo.live.watch.dto;

import lombok.Data;

/**
 * 学员上报小节学习进度（D101）。观众 id 只从 JWT 取。
 */
@Data
public class CourseLessonProgressRequest {

    /** 本课小节节点 id */
    private Long lessonNodeId;

    /** open / heartbeat / scroll_bottom / live_ended */
    private String event;

    /** 本次增量秒，≥0；缺省 0 */
    private Long deltaLearnDurationSec;

    /** 进度条当前位置秒，≥0；与库内取 max */
    private Integer progressBarSec;

    /** 0～100；与库内取 max */
    private Integer progressPercent;
}
