package com.zhibo.liveprovider.dto;

/**
 * 云侧回放/录制资源一条（create-demand 的 record_id = {@code id}，微吼 integer）。
 */
public record ProviderRecordItem(
        Long id,
        String title,
        String duration,
        String createdTime,
        String coverUrl,
        /** 体积（KB），微吼 {@code storage} */
        String storage,
        /** 到期时间，微吼 {@code save_time} → 本站 {@code saveTime} */
        String saveTime
) {
}
