package com.zhibo.live.watch.dto;

import lombok.Data;

/**
 * 观看端首页列表项（精简字段）。
 */
@Data
public class WatchActivityItem {

    private Long id;
    private String title;
    private Integer state;
    private String startTime;
    private String coverUrl;
}
