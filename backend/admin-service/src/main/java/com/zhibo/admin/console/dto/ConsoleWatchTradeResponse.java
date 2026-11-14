package com.zhibo.admin.console.dto;

import lombok.Data;

/**
 * {@code GET /api/v1/activities/console/watch-trade/{id}}：中控台「观看数据」成交三项汇总。
 */
@Data
public class ConsoleWatchTradeResponse {

    private String id;
    /** 直播间成交金额：本场已付 {@code pay_amount} 求和；两位小数字符串。 */
    private String dealAmount;
    /** 成交人数：本场已付订单 {@code username} 去重。 */
    private long dealUserCount;
    /** 成交商品数：本场已付订单行数（一单一品）。 */
    private long dealProductCount;
}
