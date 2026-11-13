package com.zhibo.live.watch.dto;

import lombok.Getter;
import lombok.Setter;

/** 与中控台 coupon-history 同形。 */
@Getter
@Setter
public class WatchCouponHistoryItem {

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
