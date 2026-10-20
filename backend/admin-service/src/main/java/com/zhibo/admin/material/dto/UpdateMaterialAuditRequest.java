package com.zhibo.admin.material.dto;

import lombok.Data;

@Data
public class UpdateMaterialAuditRequest {
    /** 1 已通过 / 2 已驳回 */
    private Integer status;
}
