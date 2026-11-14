package com.zhibo.admin.console.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

/** 中控台带货明细一行（D124）：本场订单按 username×spu 聚合。 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ConsoleCommerceDetailItem {

    private String username;
    private String nickname;
    private String avatarUrl;
    private String unionid;
    private String spuId;
    private String productName;
    private int clickCount;
    private String lastClickTime;
    private int orderCount;
    private int unpaidCount;
    private int paidSuccessCount;
    /** unpaid / paid / refunded */
    private String payStatus;
    private String liveDealAmount;
    private int totalPaidOrderCount;
    private String totalPaidAmount;
    private String phoneMasked;
    private String followerName;
    private Long followerEmployeeId;
}
