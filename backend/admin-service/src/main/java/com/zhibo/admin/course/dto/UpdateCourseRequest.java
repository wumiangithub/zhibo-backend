package com.zhibo.admin.course.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

/** 课程基本信息更新；字段与创建一致。 */
@Data
public class UpdateCourseRequest {

    @NotBlank(message = "请输入课程名称")
    private String title;

    private String introduction;

    private String detailHtml;

    @NotBlank(message = "请选择课程封面")
    private String coverMaterialId;

    @NotNull(message = "是否上架不能为空")
    private Boolean listed;

    private List<Long> ownerIds;

    /** 课件（有序）：素材 id + 学员权限。传空数组表示清空课件。 */
    private List<CourseMaterialInput> materials;
}
