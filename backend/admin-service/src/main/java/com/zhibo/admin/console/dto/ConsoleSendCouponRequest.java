package com.zhibo.admin.console.dto;

import java.util.List;
import lombok.Getter;
import lombok.Setter;

/** {@code POST /api/v1/activities/console/coupons/send/{id}} Body。 */
@Getter
@Setter
public class ConsoleSendCouponRequest {

    private Long couponId;
    private String couponNo;
    private String couponName;
    private String couponAmount;
    private String couponStartDate;
    private String couponEndDate;
    private Boolean allSku;
    private List<String> deptNames;
    private List<String> skuNames;
}
