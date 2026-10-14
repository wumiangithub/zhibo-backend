package com.zhibo.admin.activity.dto;

import lombok.Data;

@Data
public class AuditRecordItem {

    private String id;
    /** passed / rejected */
    private String result;
    /** 通过时不输出。 */
    private String reason;
    private String operatorId;
    private String operatorName;
    private String createdAt;
}
