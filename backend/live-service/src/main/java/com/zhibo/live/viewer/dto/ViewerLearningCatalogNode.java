package com.zhibo.live.viewer.dto;

import lombok.Data;

/** 课程详情 · 目录节点（扁平）。 */
@Data
public class ViewerLearningCatalogNode {

    private Long id;
    private Long parentId;
    /** 1 章节 / 2 子课程 / 3 小节 */
    private Integer nodeType;
    private String title;
    /** 仅小节：1 直播 / 2 图片 / 3 视频 / 4 文档 / 5 文章 */
    private Integer contentKind;
    private String contentId;
    private String coverUrl;
    /** 章节：直属小节数；子课程：其下全部小节数 */
    private Integer lessonCount;
    /** 小节类型中文：直播 / 图片 / 视频 / 文档 / 文章 */
    private String contentKindLabel;
    /** 直播「支持回放」等副文案；无则省略 */
    private String metaLabel;
    /** 仅小节：本学员参与状态 0 未开始 / 1 进行中 / 2 已完成（D101） */
    private Integer participateStatus;
    /** 仅小节：本学员进度百分 0～100；无进度行可省略 */
    private Integer progressPercent;
}
