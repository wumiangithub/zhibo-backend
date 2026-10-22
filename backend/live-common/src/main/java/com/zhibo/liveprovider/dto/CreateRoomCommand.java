package com.zhibo.liveprovider.dto;

/**
 * @param title             活动标题
 * @param startTime         yyyy-MM-dd HH:mm:ss
 * @param type              1-4
 * @param introduction      简介
 * @param recordId          录制 ID（type=4）
 * @param coverUrl          封面 URL
 * @param ultraLowLatency   超低延迟 → 微吼 no_delay_webinar
 * @param isTiming          录播是否定时 → 微吼 is_timing；null 按 true
 */
public record CreateRoomCommand(
        String title,
        String startTime,
        Integer type,
        String introduction,
        String recordId,
        String coverUrl,
        Boolean ultraLowLatency,
        Boolean isTiming
) {
}
