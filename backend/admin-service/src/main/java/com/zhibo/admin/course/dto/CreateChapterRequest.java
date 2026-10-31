package com.zhibo.admin.course.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class CreateChapterRequest {

    /** 父节点；根下为 0。有子课程后必须为某子课程 id */
    @NotNull(message = "parentId 不能为空")
    private Long parentId;

    @NotBlank(message = "请输入章节标题")
    private String title;
}
