package com.zhibo.live.watch.dto;

import lombok.Getter;
import lombok.Setter;

/** {@code GET /api/v1/watch/coupons/{id}} 列表项。 */
@Getter
@Setter
public class WatchCouponItem {

    private Long couponId;
    private String couponNo;
    private String couponName;
    private String couponAmount;
    private String couponStartDate;
    private String couponEndDate;
    private boolean allSku;
    private String deptNames;
    private String skuNames;
    private String lastSentTime;
    /** 最近发送员工；侧栏领取传订单中心 */
    private Long lastSenderEmployeeId;
    private String lastSenderNickname;
    /** 岗位表解析的部门；无岗位可省略 */
    private Long lastSenderDeptId;
    private String lastSenderDeptName;
}
