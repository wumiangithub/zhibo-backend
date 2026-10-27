package com.zhibo.admin.activity.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.ArrayList;
import java.util.List;
import lombok.Data;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AudienceListItem {

    private Long id;
    private Long viewerId;
    private String nickname;
    private String avatarUrl;
    private int entitlementStatus;
    private String inviterDisplayName;
    private String inviterKind;
    private String inviteTime;
    private Long employeeInviterId;
    private String employeeInviteTime;
    private Long importerId;
    private String importerName;
    private String importTime;
    /** 观众全局标签 id（与 tagLabels 同序；来自 viewer_tag_member） */
    private List<Long> tagIds = new ArrayList<>();
    private List<String> tagLabels = new ArrayList<>();
    private long stayDurationSec;
    private long totalWatchDurationSec;
    private long liveWatchDurationSec;
    private long replayWatchDurationSec;
    private long commentCount;
    private String dealAmount = "0.00";
}
