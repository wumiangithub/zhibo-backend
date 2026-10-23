package com.zhibo.admin.activity.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * GET /api/v1/activities/records/{id}/download/{recordId} 的 data。
 * 对应微吼 check-download；status=0 时后端已提交打包任务，本次仍回检查结果。
 * downloadUrl 约 7 天有效。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ActivityRecordDownloadResponse {

    /** 微吼 record_id（JSON number） */
    private Long recordId;

    /** 下载地址；status=1 时可用 */
    private String downloadUrl;

    /**
     * 打包状态：0 未开始 / 1 已完成 / 2 进行中 / 3 失败
     */
    private Integer status;

    /** 打包进度 0～100 */
    private Integer progress;
}
