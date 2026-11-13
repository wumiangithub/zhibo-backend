package com.zhibo.liveprovider.dto;

import java.util.List;

/**
 * 微吼 {@code get-webinar-switch-list} 响应摘要（D119）。
 * 顶层 {@code startTime}≈计划预告；{@code startDate}/{@code endDate}≈实际首开/末结。
 */
public record ProviderWebinarSwitchList(
        String startTime,
        String endTime,
        String startDate,
        String endDate,
        Integer totalLiveTime,
        List<ProviderWebinarSwitchItem> switchList
) {
}
