package com.zhibo.admin.console.dto;

import lombok.Data;

/**
 * {@code GET /api/v1/activities/console/summary/{id}} 响应：中控台页壳（D110）。
 * 字段对齐观看 {@code /watch/summary}，无观众售卖门禁、不做云态对账。
 */
@Data
public class ConsoleSummaryResponse {

    private String id;
    private String provider;
    private String title;
    /** 活动详情封面；对齐观看 summary 的 coverUrl。 */
    private String coverUrl;
    private Integer state;
    private Integer type;
    private String startTime;
    private String endTime;
    private Boolean playbackEnabled;
    private String playbackValidityType;
    private Integer playbackValidityDays;
    private Boolean playbackExpired;
    private Boolean playbackSpeedAllow;
    private Boolean playbackFastForwardAllow;
}
