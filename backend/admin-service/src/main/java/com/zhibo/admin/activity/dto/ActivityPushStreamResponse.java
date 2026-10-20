package com.zhibo.admin.activity.dto;

import lombok.Data;

/** GET /api/v1/activities/push-stream/{id} 的 data。不含微吼密钥或原始响应。 */
@Data
public class ActivityPushStreamResponse {

    /** 推流服务器，对应微吼 stream_address */
    private String streamAddress;

    /** 串流密钥，对应微吼 stream_code */
    private String streamCode;

    /** 有效期，对应微吼 expire_time；没有则不返回 */
    private String expireTime;
}
