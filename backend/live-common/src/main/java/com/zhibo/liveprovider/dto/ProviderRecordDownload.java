package com.zhibo.liveprovider.dto;

/**
 * 云侧回放下载检查结果（微吼 {@code /v3/webinars/record/check-download}）。
 *
 * <p>{@code status}：0 未开始 / 1 已完成 / 2 进行中 / 3 失败。
 * 已完成时 {@code downloadUrl} 可用（微吼侧约 7 天有效）。
 */
public record ProviderRecordDownload(
        Long recordId,
        String downloadUrl,
        Integer status,
        Integer progress
) {
}
