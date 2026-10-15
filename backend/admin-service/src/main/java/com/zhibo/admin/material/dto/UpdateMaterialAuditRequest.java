package com.zhibo.admin.material.dto;

import lombok.Data;

@Data
public class UpdateMaterialAuditRequest {
    /** passed / rejected */
    private String status;
}
