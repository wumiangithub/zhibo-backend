package com.zhibo.admin.activity.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * 改活动基本信息 Tab。type / provider / viewerStyle / ultraLowLatency 不可改。
 */
@Data
public class UpdateActivityRequest {

    private String title;
    private String startTime;
    @Size(max = 256, message = "简介不能超过256个字")
    private String introduction;
    private String endTime;
    private String coverUrl;
    @Size(max = 100000, message = "详情过长")
    private String detailHtml;
    private Boolean autoList;
    /** 全量覆盖主播；传了则至少 1 人 */
    private List<Long> hostIds;
    private List<Long> ownerIds;
    private List<CreateActivityRequest.ProductBinding> products;
    /** 全量覆盖课件；传 [] 清空 */
    private List<String> coursewareMaterialIds;

    /** 废弃：请求里带了忽略（创建后不可改） */
    private String viewerStyle;
    /** 废弃：请求里带了忽略 */
    private Boolean ultraLowLatency;
    /** 废弃：前端不再传 */
    private Boolean virtualAvatar;
    private String warmupType;
    private String warmupUrl;
    private String promoCoverUrl;
}
