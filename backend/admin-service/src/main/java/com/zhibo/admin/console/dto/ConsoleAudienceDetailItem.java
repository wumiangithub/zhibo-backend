package com.zhibo.admin.console.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

/** 中控台观众明细一行（D123）。 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ConsoleAudienceDetailItem {

    private String username;
    private String nickname;
    private String avatarUrl;
    private String phoneMasked;
    private String followerName;
    private Long followerEmployeeId;
    private String firstEnterTime;
    private String latestEnterTime;
    private long liveWatchDurationSec;
    private long replayWatchDurationSec;
    private long totalWatchDurationSec;
    private String leaveTime;
    private int enterCount;
    private boolean online;
    private int productClickCount;
    private int pendingPayCount;
    private int purchaseCount;
    private int totalPurchaseCount;
    private String totalPurchaseAmount;
    private int commentCount;
    private int couponReceiveCount;
}
