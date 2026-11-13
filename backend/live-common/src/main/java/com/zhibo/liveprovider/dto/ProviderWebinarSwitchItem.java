package com.zhibo.liveprovider.dto;

/**
 * 微吼 {@code get-webinar-switch-list} 的 {@code switch_list[]} 一条（D119）。
 */
public record ProviderWebinarSwitchItem(
        Long id,
        String startTime,
        String endTime,
        /** 0 直播 / 1 录制 / 2 彩排（以微吼为准） */
        Integer type,
        Integer startType,
        Integer endType
) {
}
