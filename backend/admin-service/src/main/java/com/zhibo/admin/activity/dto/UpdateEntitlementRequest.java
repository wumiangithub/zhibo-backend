package com.zhibo.admin.activity.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class UpdateEntitlementRequest {

    @NotNull(message = "entitlementStatus 不能为空")
    private Integer entitlementStatus;
}
