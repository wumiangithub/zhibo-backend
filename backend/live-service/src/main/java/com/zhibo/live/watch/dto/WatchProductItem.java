package com.zhibo.live.watch.dto;

import lombok.Data;

/** 观看端关联商品快照（与创建/带货绑定同源）。 */
@Data
public class WatchProductItem {

    /** 订单中心 spuId，十进制字符串。 */
    private String spuId;
    /** 绑定时商品名称快照。 */
    private String productName;
}
