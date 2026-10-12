package com.zhibo.admin.upload.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * {@code GET /api/uploads/tos-sts} 响应；字段对齐前端 {@code tosConfig}。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TosStsResponse {

    private String region;
    private String endpoint;
    private String bucketName;
    private String accessKeyId;
    private String secretAccessKey;
    private String sessionToken;
    /** ISO-8601 UTC，带 {@code Z}。 */
    private String expiredTime;
}
