package com.zhibo.admin.activity.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 创建活动请求（对外用 activity 语义；落到微吼时映射为 webinar 字段）。
 */
@Data
public class CreateActivityRequest {

    /** 活动标题 → 微吼 subject */
    @NotBlank(message = "标题不能为空")
    private String title;

    /** 开始时间，格式 yyyy-MM-dd HH:mm:ss → 微吼 start_time */
    @NotBlank(message = "开始时间不能为空")
    private String startTime;

    /**
     * 直播模式 → 微吼 webinar_type。
     * 1 音频 / 2 视频 / 3 互动；创建后不可改。
     */
    @NotNull(message = "直播类型不能为空")
    @Min(value = 1, message = "直播类型无效")
    @Max(value = 3, message = "直播类型无效")
    private Integer type;
}
