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

    /**
     * 课件（有序）。与 {@link #materialIds} 二选一；同时传时以本字段为准。
     * 项含素材 id + 学员权限。
     */
    private List<CourseMaterialInput> materials;

    /** 兼容旧客户端：仅 id 有序数组，权限按扩展名默认。优先用 {@link #materials}。 */
    private List<String> materialIds;
}
