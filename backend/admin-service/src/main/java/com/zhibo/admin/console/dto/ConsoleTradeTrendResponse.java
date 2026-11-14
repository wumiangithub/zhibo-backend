package com.zhibo.admin.console.dto;

import java.util.List;
import lombok.Data;

/**
 * {@code GET /api/v1/activities/console/trade-trend/{id}}：中控台「交易数据」成交趋势。
 */
@Data
public class ConsoleTradeTrendResponse {

    private String id;
    private String startTime;
    private String endTime;
    private int bucketSeconds;
    private List<ConsoleTradeTrendPointResponse> points;
}
