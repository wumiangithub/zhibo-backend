package com.zhibo.admin.activity.dto;

import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * GET /api/v1/activities/records/{id}/download/{recordId} 的 data（D88）。
 * 本库已有 {@code tos_url} 时回 {@code tosUrl}（及兼容字段 {@code downloadUrl}）；
 * 否则 check-download 后可异步落 TOS。
 * 字段名用 packStatus，避免与本库行 status INT 枚举混淆。
 */
@Data
@NoArgsConstructor
public class ActivityRecordDownloadResponse {

    /** 云侧 record_id（JSON number） */
    private Long recordId;

    /**
     * 本站 TOS/CDN；有 {@code tosUrl} 时与之同值（兼容旧前端）。
     */
    private String downloadUrl;

    /** 本站 TOS/CDN；有值时前端直接走下载封装 */
    private String tosUrl;

    /**
     * 打包/落库态：0 未开始 / 1 可下载（本站已就绪）/ 2 处理中 / 3 失败
     */
    private Integer packStatus;

    /** 进度 0～100；本站已就绪时为 100 */
    private Integer progress;

    /**
     * 本站内存任务开始下载时间 {@code yyyy-MM-dd HH:mm:ss}（上海时区）。
     * 仅当 {@code packStatus=2} 且 JVM 确有 ingest in-flight 时返回。
     */
    private String downloadStartedTime;

    /** 已有 TOS：{@code packStatus=1}，带 {@code tosUrl}+{@code downloadUrl}。 */
    public static ActivityRecordDownloadResponse ready(long recordId, String tosUrl) {
        ActivityRecordDownloadResponse r = new ActivityRecordDownloadResponse();
        r.setRecordId(recordId);
        String u = tosUrl == null ? null : tosUrl.trim();
        if (u != null && !u.isEmpty()) {
            r.setTosUrl(u);
            r.setDownloadUrl(u);
        }
        r.setPackStatus(1);
        r.setProgress(100);
        return r;
    }

    /** 尚无 TOS：只回 pack 态。 */
    public static ActivityRecordDownloadResponse packing(
            long recordId, Integer packStatus, Integer progress) {
        ActivityRecordDownloadResponse r = new ActivityRecordDownloadResponse();
        r.setRecordId(recordId);
        r.setPackStatus(packStatus);
        r.setProgress(progress);
        return r;
    }
}
