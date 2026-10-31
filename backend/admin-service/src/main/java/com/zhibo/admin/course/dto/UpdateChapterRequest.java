package com.zhibo.admin.course.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class UpdateChapterRequest {

    @NotNull(message = "章节 id 不能为空")
    private Long id;

    @NotBlank(message = "请输入章节标题")
    private String title;
}
