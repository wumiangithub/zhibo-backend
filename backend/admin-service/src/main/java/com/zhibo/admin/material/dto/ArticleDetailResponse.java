package com.zhibo.admin.material.dto;

import lombok.Data;

/** 文章详情。列表接口不返回正文。 */
@Data
public class ArticleDetailResponse {
    private String id;
    private String groupId;
    private String title;
    private String author;
    private String content;
    /** 0 待审核 / 1 已通过 / 2 已驳回 */
    private Integer aiAuditStatus;
    /** 0 待审核 / 1 已通过 / 2 已驳回 */
    private Integer riskAuditStatus;
}
