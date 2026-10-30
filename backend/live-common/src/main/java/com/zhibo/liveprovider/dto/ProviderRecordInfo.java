package com.zhibo.liveprovider.dto;

/**
 * 云侧回放详情（微吼 {@code GET /v3/webinars/record/get-info}）。
 *
 * <p>供本站 {@code activity_record} 落库；不映射微吼 {@code save_time}。
 */
public record ProviderRecordInfo(
        /** 云侧回放 id ← {@code id} */
        Long recordId,
        /** 云侧活动 id ← {@code webinar_id}；无则 null */
        Long webinarId,
        /** 回放名称 ← {@code name} */
        String title,
        /** 时长（秒）← {@code duration} */
        Integer durationSec,
        /** 封面 ← {@code img_url} */
        String coverUrl,
        /** 体积（KB）← {@code storage} */
        Long storageKb,
        /**
         * 云侧生成时间 ← {@code created_at}；
         * 与 {@link ProviderRecordItem#createdTime()} 同风格（如 {@code yyyy-MM-dd HH:mm:ss}）。
         */
        String createdTime
) {
}
