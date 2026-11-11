package com.zhibo.admin.activity.dto;

import lombok.Data;

@Data
public class CreateAudienceRequest {

    /** 与 uid 二选一 */
    private String phone;

    /** 与 phone 二选一；viewer 数字主键 */
    private Long uid;
}
