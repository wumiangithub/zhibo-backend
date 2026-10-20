package com.zhibo.admin.course.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CourseAuditResultResponse {

    private String id;
    private String riskStatus;
    private String shelfStatus;
}
