package com.zhibo.admin.course.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

@Data
public class CreateCourseRequest {

    @NotBlank(message = "请输入课程名称")
    private String title;

    private String introduction;

    private String detailHtml;

    @NotBlank(message = "请选择课程封面")
    private String coverMaterialId;

    @NotNull(message = "是否自动上架不能为空")
    private Boolean autoList;

    private List<Long> ownerIds;

    private List<String> materialIds;
}
