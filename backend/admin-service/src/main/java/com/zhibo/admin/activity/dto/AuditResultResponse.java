package com.zhibo.admin.activity.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AuditResultResponse {

    private String id;
    private String riskStatus;
    private String shelfStatus;
}
