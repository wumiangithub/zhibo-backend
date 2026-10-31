package com.zhibo.live.watch.dto;

import lombok.Data;

/**
 * {@code GET /api/v1/watch/sdk-sign} 响应：仅 SDK 签名与身份回显（D95；页壳见 {@link WatchSummaryResponse}）。
 */
@Data
public class WatchSdkResponse {

    private String id;
    private String provider;
    private String nickname;
    /** 观众数字 id；与 sdk.email 一致。 */
    private Long viewerId;
    /** SDK 初始化字段（含服务端签名）。 */
    private SdkPayload sdk;
}
