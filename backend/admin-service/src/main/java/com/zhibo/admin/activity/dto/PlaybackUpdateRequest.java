package com.zhibo.admin.activity.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/** PUT /api/activities/{id}/playback */
@Data
public class PlaybackUpdateRequest {

    @NotNull(message = "playbackEnabled 不能为空")
    private Boolean playbackEnabled;

    /** forever / days；开启时必填 */
    private String playbackValidityType;

    /** type=days 时必填，≥1 */
    private Integer playbackValidityDays;

    private Boolean playbackSpeedAllow;

    private Boolean playbackFastForwardAllow;
}
