package com.zhibo.admin.activity.dto;

import lombok.Data;

/**
 * 列表项：只暴露前端常用字段，微吼原始 webinar_* 在对接层消化掉。
 */
@Data
public class ActivitySummary {

    private Long id;
    private String title;
    private String startTime;
    private String coverUrl;
    /** 0全部场景下的真实状态：1直播 2预告 3结束 4点播 5回放 */
    private Integer state;
    /** 1音频 2视频 3互动 */
    private Integer type;
    private String shareLink;
    private String createdAt;
}
