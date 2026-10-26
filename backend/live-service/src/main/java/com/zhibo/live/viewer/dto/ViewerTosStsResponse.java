package com.zhibo.live.viewer.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * {@code GET /api/v1/viewer/uploads/tos-sts}。
 * 前 7 项对齐管理端 tos-sts；另附 keyPrefix / cdnUrl 方便小程序。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ViewerTosStsResponse {

    private String region;
    private String endpoint;
    private String bucketName;
    private String accessKeyId;
    private String secretAccessKey;
    private String sessionToken;
    /** ISO-8601 UTC，带 Z。 */
    private String expiredTime;
    /** 固定 viewer/avatar/ */
    private String keyPrefix;
    /** VOLC_TOS_CDN_URL；未配则为空串。 */
    private String cdnUrl;
}
