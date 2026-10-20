package com.zhibo.admin.material.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class MaterialAuditRecordItem {

    private String id;
    /** ai / risk */
    private String action;
    /** passed / rejected */
    private String result;
    private Long operatorId;
    private String operatorName;
    /** 操作人部门展示名；无则不输出 */
    private String departmentName;
    private String createdTime;
}
