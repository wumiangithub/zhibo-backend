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
    /** 计划/实际开始时间，yyyy-MM-dd HH:mm:ss（微吼 info.start_time）。 */
    private String startTime;
    /** 结束时间；微吼占位 0000-00-00… 时不设值（jackson non_null 不输出）。 */
    private String endTime;
    /** SDK 初始化字段（含服务端签名）。 */
    private SdkPayload sdk;
    /** 与旧 watch 接口相同，供 SDK 失败时 iframe 兜底。 */
    private String embedUrl;
}
