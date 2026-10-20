package com.zhibo.admin.activity.dto;

import lombok.Data;

/** GET /api/v1/activities/stream-status/{id} 的 data。不含微吼密钥或原始响应。 */
@Data
public class ActivityStreamStatusResponse {

    /** 是否有流：0 无，1 有。对应微吼 data.status */
    private Integer status;
}
