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
    /** 计划/实际开始时间，yyyy-MM-dd HH:mm:ss（微吼 info.start_time）。 */
    private String startTime;
    /** 结束时间；微吼占位 0000-00-00… 时不设值（jackson non_null 不输出）。 */
    private String endTime;
    /** 全屏嵌入观看页（已拼 email / nickname，适合 iframe） */
    private String embedUrl;
}
