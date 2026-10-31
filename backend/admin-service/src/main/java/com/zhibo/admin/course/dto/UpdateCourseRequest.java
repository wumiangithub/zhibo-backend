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

    @NotNull(message = "是否自动上架不能为空")
    private Boolean autoList;

    private List<Long> ownerIds;

    /**
     * 课件（有序）。与 {@link #materialIds} 二选一；同时传时以本字段为准。
     * 传空数组表示清空课件。
     */
    private List<CourseMaterialInput> materials;

    /** 兼容旧客户端：仅 id 有序数组，权限按扩展名默认。优先用 {@link #materials}。 */
    private List<String> materialIds;
}
