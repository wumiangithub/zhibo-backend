package com.zhibo.liveprovider.dto;

/**
 * 云侧回放/录制资源一条（create-demand 的 record_id = {@code id}）。
 */
public record ProviderRecordItem(
        String id,
        String title,
        String duration,
        String createdTime,
        String coverUrl
) {
}
