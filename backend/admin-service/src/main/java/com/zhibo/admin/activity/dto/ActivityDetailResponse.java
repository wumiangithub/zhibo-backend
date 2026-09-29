package com.zhibo.admin.activity.dto;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class ActivityDetailResponse extends ActivitySummary {

    private String providerRoomId;
    /** 兼容字段：仅 vhall 时等于 providerRoomId */
    private String vhallWebinarId;
    private String introduction;
    private String endTime;
    private Integer verify;
    private Integer pv;
    private String fullEmbedShareLink;
    private Boolean ultraLowLatency;
    private Boolean virtualAvatar;
    private String warmupType;
    private String warmupUrl;
    private String promoCoverUrl;
    private String detailHtml;
}
