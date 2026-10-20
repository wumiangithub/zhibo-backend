package com.zhibo.admin.course.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CourseAuditItem {

    private String id;
    private String action;
    private String result;
    private String reason;
    private Long operatorId;
    private String operatorName;
    /** 操作人部门展示名；无则不输出 */
    private String departmentName;
    private String createdTime;
}
