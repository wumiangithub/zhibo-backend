package com.zhibo.live.watch.dto;

import lombok.Data;

/**
 * 观看端进房信息：已拼好身份参数的嵌入地址。
 */
@Data
public class WatchResponse {

    private Long id;
    private String title;
    private Integer state;
    private Integer type;
    private String nickname;
    private String guestId;
    /** 全屏嵌入观看页（已拼 email / nickname，适合 iframe） */
    private String embedUrl;
}
