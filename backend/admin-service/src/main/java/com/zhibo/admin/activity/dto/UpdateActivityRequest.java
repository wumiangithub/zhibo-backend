package com.zhibo.admin.activity.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 改活动：type / provider 不可改。
 */
@Data
public class UpdateActivityRequest {

    private String title;
    private String startTime;
    @Size(max = 256, message = "简介不能超过256个字")
    private String introduction;
    private String endTime;
    private String viewerStyle;
    private Boolean ultraLowLatency;
    private Boolean virtualAvatar;
    private String coverUrl;
    private String warmupType;
    private String warmupUrl;
    private String promoCoverUrl;
    @Size(max = 100000, message = "详情过长")
    private String detailHtml;
}
