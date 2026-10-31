package com.zhibo.admin.course.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class CreateSubcourseRequest {

    @NotBlank(message = "请输入子课程名称")
    private String title;

    @NotBlank(message = "请选择子课程封面")
    private String coverMaterialId;

    /**
     * 首个子课程且根下已有章节/小节时须为 true，表示确认迁入。
     * 其它情况可省略或 false。
     */
    private Boolean moveRootContents;
}
