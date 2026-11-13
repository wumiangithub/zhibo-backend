package com.zhibo.admin.console.dto;

import lombok.Getter;
import lombok.Setter;

/** 中控台 / 观看端优惠券聊天历史单条（同形）。 */
@Getter
@Setter
public class ConsoleCouponHistoryItem {

    private Long id;
    private String senderName;
    private Long timestamp;
    private Long couponId;
    private String couponName;
    private String couponAmount;
    private String couponStartDate;
    private String couponEndDate;
    private Long employeeId;
    /** 发送人部门 id（岗位表）；无则省略 */
    private Long deptId;
    private String deptName;
}
