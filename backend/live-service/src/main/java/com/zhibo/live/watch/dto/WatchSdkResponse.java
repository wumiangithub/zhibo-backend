package com.zhibo.live.watch.dto;

import lombok.Data;

/**
 * {@code GET /api/watch/{id}/sdk} 响应：SDK 初始化载荷 + 旧嵌入地址兜底。
 */
@Data
public class WatchSdkResponse {

    private Long id;
    private String title;
    private Integer state;
    private Integer type;
    private String nickname;
    private String guestId;
    /** SDK 初始化字段（含服务端签名）。 */
    private SdkPayload sdk;
    /** 与旧 watch 接口相同，供 SDK 失败时 iframe 兜底。 */
    private String embedUrl;
}
