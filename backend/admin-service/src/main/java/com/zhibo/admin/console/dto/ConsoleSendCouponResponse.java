package com.zhibo.admin.console.dto;

import lombok.Getter;
import lombok.Setter;

/** {@code POST /api/v1/activities/console/coupons/send/{id}} 响应。 */
@Getter
@Setter
public class ConsoleSendCouponResponse {

    private Long commentId;
    private Long couponId;
    private String couponNo;
    private String couponName;
    private String couponAmount;
    private String couponStartDate;
    private String couponEndDate;
    private Long employeeId;
    private String nickname;
    /** 发送人部门 id（岗位表）；无则省略 */
    private Long deptId;
    private String deptName;
    private String commentTime;
}
