package com.zhibo.admin.console.dto;

import lombok.Data;

/**
 * 中控台「商品」列表行：本站绑定快照 + 显示态 + 本场已售。
 * 封面/价/服务期/类型由前端直连订单中心批量详情补齐。
 */
@Data
public class ConsoleProductItem {

    /** 订单中心 spuId，十进制字符串 */
    private String spuId;

    /** 绑定时名称快照 */
    private String productName;

    /** 直播间是否展示该商品 */
    private boolean visible;

    /** 本场已付订单行数（一单一品）；无成交为 0 */
    private long soldCount;
}
