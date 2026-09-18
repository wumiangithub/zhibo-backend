package com.zhibo.admin.activity.dto;

import lombok.Data;

/**
 * 改活动：只允许改标题、开始时间；类型创建后不可改。
 */
@Data
public class UpdateActivityRequest {

    /** 活动标题 → 微吼 subject；不传则不改 */
    private String title;

    /** 开始时间 yyyy-MM-dd HH:mm:ss → 微吼 start_time（裁到分钟）；不传则不改 */
    private String startTime;
}
