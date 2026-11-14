package com.zhibo.admin.console.dto;

import lombok.Data;

/**
 * {@code GET /api/v1/activities/console/trade-stats/{id}}：中控台「交易数据」左侧 KPI。
 */
@Data
public class ConsoleTradeStatsResponse {

    private String id;
    /** 成交人数：本场已付订单 username 去重。 */
    private long dealUserCount;
    /** 成交商品数：本场已付订单行数（一单一品）。 */
    private long dealProductCount;
    /** 商品点击人数：本场 activity_product_click username 去重。 */
    private long productClickUserCount;
    /**
     * 成交转化率：成交人数 / 商品点击人数 × 100；一位小数字符串，如 {@code "12.5"}；
     * 点击人数为 0 时为 {@code "0.0"}。不含百分号。
     */
    private String dealConversionRate;
    /**
     * 平均客单价：本场已付成交金额 / 成交人数；两位小数字符串；成交人数为 0 时为 {@code "0.00"}。
     */
    private String avgOrderAmount;
    /** 未付款人数：本场 order_status=1（待付款）订单 username 去重。 */
    private long unpaidUserCount;
}
