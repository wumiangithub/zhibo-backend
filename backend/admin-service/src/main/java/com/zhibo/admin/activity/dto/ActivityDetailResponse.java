package com.zhibo.admin.activity.dto;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class ActivityDetailResponse extends ActivitySummary {

    private String introduction;
    private String endTime;
    private Integer verify;
    private Integer pv;
    private String fullEmbedShareLink;
}
