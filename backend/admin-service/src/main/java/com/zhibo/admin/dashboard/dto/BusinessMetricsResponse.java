package com.zhibo.admin.dashboard.dto;

import lombok.Data;

/**
 * 工作台概况「经营数据」七项指标。
 * 金额为两位小数十进制字符串（单位元），避免浮点误差；前端加 ¥ 千分位。
 */
@Data
public class BusinessMetricsResponse {

    /** 今日访客数：viewer.last_visit_time 落在亚洲/上海当天 */
    private long todayVisitorCount;

    /** 今日新增用户数：viewer.created_time 落在亚洲/上海当天 */
    private long todayNewUserCount;

    /** 今日支付金额（元），如 "26800.00" */
    private String todayPayAmount;

    /** 今日订单数：已支付且 pay_time 在亚洲/上海当天 */
    private long todayOrderCount;

    /** 累计支付人数：已支付订单按 customer_id 去重 */
    private long totalPayUserCount;

    /** 累计用户：viewer 表行数 */
    private long totalUserCount;

    /** 累计支付金额（元），如 "111126800.00" */
    private String totalPayAmount;

    /** 服务端统计时刻，Asia/Shanghai，yyyy-MM-dd HH:mm:ss */
    private String statsTime;
}
