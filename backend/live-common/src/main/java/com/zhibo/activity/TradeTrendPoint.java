package com.zhibo.activity;

import java.math.BigDecimal;

/** 成交趋势单桶：时间起点 + 成交金额 + 销量（已付订单行数）。 */
public record TradeTrendPoint(String time, BigDecimal dealAmount, int soldCount) {
}
