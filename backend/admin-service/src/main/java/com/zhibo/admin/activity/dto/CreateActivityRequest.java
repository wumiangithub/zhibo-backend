package com.zhibo.admin.activity.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 创建活动请求（对外用 activity 语义；落到微吼时映射为 webinar 字段）。
 */
@Data
public class CreateActivityRequest {

    /** 活动标题 → 微吼 subject；≤64 字 */
    @NotBlank(message = "标题不能为空")
    @Size(max = 64, message = "标题不能超过64个字")
    private String title;

    /** 开始时间，格式 yyyy-MM-dd HH:mm:ss → 微吼 start_time */
    @NotBlank(message = "开始时间不能为空")
    private String startTime;

    /**
     * 结束时间（可选）。仅本站校验须晚于 startTime；微吼 create 无 end_time，不转发。
     */
    private String endTime;

    /** 简介（可选）→ 微吼 introduction；≤256 字 */
    @Size(max = 256, message = "简介不能超过256个字")
    private String introduction;

    /**
     * 直播模式 → 微吼 webinar_type。
     * 1 音频 / 2 视频 / 3 互动；无 4（录播走 create-demand，本迭代不支持）。
     */
    @NotNull(message = "直播类型不能为空")
    @Min(value = 1, message = "直播类型无效")
    @Max(value = 3, message = "直播类型无效")
    private Integer type;
}
