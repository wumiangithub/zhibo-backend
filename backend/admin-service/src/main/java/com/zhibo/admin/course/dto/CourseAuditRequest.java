package com.zhibo.admin.course.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class CourseAuditRequest {

    /** passed / rejected */
    @NotBlank(message = "审核结果不能为空")
    private String result;

    /** 仅驳回时填写。 */
    private String reason;
}
