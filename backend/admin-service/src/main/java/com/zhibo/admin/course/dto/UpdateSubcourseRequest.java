package com.zhibo.admin.course.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class UpdateSubcourseRequest {

    @NotNull(message = "子课程 id 不能为空")
    private Long id;

    @NotBlank(message = "请输入子课程名称")
    private String title;

    @NotBlank(message = "请选择子课程封面")
    private String coverMaterialId;
}
