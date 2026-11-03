package com.zhibo.live.viewer.dto;

import lombok.Data;

/** 学员小节内容（H5 学习页）。 */
@Data
public class ViewerLearningLessonContentResponse {

    private Long lessonNodeId;
    private String title;
    /** 1 直播 / 2 图片 / 3 视频 / 4 文档 / 5 文章 */
    private Integer contentKind;
    private String contentId;
    private Integer participateStatus;
    private Integer progressPercent;
    private Integer progressBarSec;
    /** 素材类 URL */
    private String materialUrl;
    private String mimeType;
    private String materialName;
    /** 仅文章正文 HTML */
    private String articleHtml;
    /** 仅文章作者 */
    private String author;
    /** 仅直播：活动 UUID */
    private String activityId;
}
