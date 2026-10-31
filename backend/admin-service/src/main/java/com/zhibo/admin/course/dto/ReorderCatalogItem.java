package com.zhibo.admin.course.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class ReorderCatalogItem {

    @NotNull(message = "节点 id 不能为空")
    private Long id;

    /** 目标父节点：目录上下文（根 0 / 子课程）或章节 id（仅小节） */
    @NotNull(message = "parentId 不能为空")
    private Long parentId;
}
