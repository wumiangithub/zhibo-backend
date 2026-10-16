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
    private String aiAuditStatus;
    private String riskAuditStatus;
}
