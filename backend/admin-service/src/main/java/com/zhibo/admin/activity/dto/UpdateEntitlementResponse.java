package com.zhibo.admin.activity.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class UpdateEntitlementResponse {

    private Long id;
    private int entitlementStatus;
}
