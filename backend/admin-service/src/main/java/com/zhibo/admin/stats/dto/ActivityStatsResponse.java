package com.zhibo.admin.stats.dto;

import lombok.Data;

/**
 * 活动观看数据概览。
 */
@Data
public class ActivityStatsResponse {

    private Long id;
    private String title;
    private Integer state;
    private Integer type;
    /** 热度（微吼活动详情 pv） */
    private Integer pv;
    /** 当前在线人数（约每分钟更新；接口限频 1 次/分钟） */
    private Integer onlineCount;
}
