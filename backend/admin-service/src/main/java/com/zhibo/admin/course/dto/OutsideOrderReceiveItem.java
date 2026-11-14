package com.zhibo.admin.course.dto;

import java.math.BigDecimal;
import java.util.List;
import lombok.Data;

/** 订单中心回调的单条订单，字段对齐《课程列表_订单回调接口文档》。 */
@Data
public class OutsideOrderReceiveItem {

    private String orderId;
    private Long productId;
    private String productName;
    private String productType;
    private BigDecimal tradeAmount;
    private String username;
    private String liveRoomId;
    private List<String> courseIds;
    private String orderTime;
    private String payTime;
    private String salesmanId;
    private Integer channel;
    /** 订单中心状态码，如 1030；原样写入 activity_order.order_status。 */
    private Integer orderStatus;
}
