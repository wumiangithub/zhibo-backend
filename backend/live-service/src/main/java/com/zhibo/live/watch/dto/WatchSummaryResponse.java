package com.zhibo.live.watch.dto;

import lombok.Data;

/**
 * {@code GET /api/v1/watch/summary/{id}} 响应：观看页壳（D95；本库 Activity，无签名）。
 */
@Data
public class WatchSummaryResponse {

    private String id;
    private String provider;
    private String title;
    private Integer state;
    private Integer type;
    /** 计划/实际开始时间，yyyy-MM-dd HH:mm:ss（本库 Activity）。 */
    private String startTime;
    /** 结束时间；本库占位 0000-00-00… 时不设值（jackson non_null 不输出）。 */
    private String endTime;
    /** 本站回放开关。false 时结束页不展示「查看回放」。 */
    private Boolean playbackEnabled;
    /** forever / days；与管理端回放设置一致 */
    private String playbackValidityType;
    /** type=days 时的有效天数 */
    private Integer playbackValidityDays;
    /** 是否已超过有效期（前端禁用按钮；服务端 start 也会拦） */
    private Boolean playbackExpired;
    /** 是否允许倍速（&lt;video&gt; playbackRate） */
    private Boolean playbackSpeedAllow;
    /** 是否允许快进/拖进度（&lt;video&gt; seek） */
    private Boolean playbackFastForwardAllow;
}
