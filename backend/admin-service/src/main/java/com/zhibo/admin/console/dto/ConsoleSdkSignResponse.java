package com.zhibo.admin.console.dto;

import lombok.Data;

/**
 * {@code GET /api/v1/activities/console/sdk-sign} 响应（D110）。
 * 对齐观看 sdk-sign，用 {@code employeeId} 代替 {@code viewerId}。
 */
@Data
public class ConsoleSdkSignResponse {

    private String id;
    private String provider;
    private String nickname;
    /** 当前登录员工 {@code rs_mainkey_id}。 */
    private Long employeeId;
    private ConsoleSdkPayload sdk;
}
