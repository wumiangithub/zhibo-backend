package com.zhibo.admin.console.dto;

import lombok.Data;

/** 成交趋势单点。 */
@Data
public class ConsoleTradeTrendPointResponse {

    /** 桶起点，格式 yyyy-MM-dd HH:mm:ss。 */
    private String time;
    /** 该桶成交金额，两位小数字符串。 */
    private String dealAmount;
    /** 该桶销量（已付订单行数）。 */
    private int soldCount;
}
