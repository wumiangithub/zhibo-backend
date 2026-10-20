package com.zhibo.admin.activity.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AuditRecordItem {

    private String id;
    /** submit / audit */
    private String action;
    /** passed / rejected；submit 不输出 */
    private String result;
    /** 驳回意见；通过与发起申请不输出。 */
    private String reason;
    private Long operatorId;
    private String operatorName;
    /** 操作人部门展示名；无则不输出 */
    private String departmentName;
    private String createdTime;
}
