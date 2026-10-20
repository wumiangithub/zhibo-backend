package com.zhibo.admin.activity.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class ActivitySummary {

    private String id;
    private String provider;
    private String title;
    private String startTime;
    private String coverUrl;
    /** 1直播 2预告 3结束 4点播 5回放 */
    private Integer state;
    /** 1音频 2视频 3互动 4录播 */
    private Integer type;
    private String viewerStyle;
    private String shareLink;
    private String createdTime;
    private String shelfStatus;
    private String riskStatus;
    private String sellType;
    private String validityLabel;
    private Boolean canBroadcast;
    private List<ActivityPerson> owners = new ArrayList<>();
    private List<ActivityProductView> products = new ArrayList<>();
}
