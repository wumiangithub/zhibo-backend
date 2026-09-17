package com.zhibo.admin.stats.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 某时间段在线人数趋势（分钟粒度）。
 */
@Data
public class OnlineTrendResponse {

    private Long id;
    private String startTime;
    private String endTime;
    private List<OnlinePoint> points = new ArrayList<>();

    @Data
    public static class OnlinePoint {
        private String time;
        private Integer count;
    }
}
