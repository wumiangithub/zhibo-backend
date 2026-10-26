package com.zhibo.live.viewer.dto;

import lombok.Data;

/** 反馈列表项。 */
@Data
public class ViewerFeedbackListItem {

    private Long id;
    /** 1～4。 */
    private int category;
    /** 分类文案。 */
    private String categoryLabel;
    /** 描述全文（列表可截断展示）。 */
    private String content;
    /** 截图张数。 */
    private int imageCount;
    /** 提交时间 yyyy-MM-dd HH:mm:ss。 */
    private String createdTime;
}
