package com.zhibo.admin.course.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

@Data
public class ReorderLessonsRequest {

    /** 父节点：根 0、子课程 id，或章节 id（仅排该父下直挂小节） */
    @NotNull(message = "请提交父节点")
    private Long parentId;

    /** 该父下全部直挂小节 id，按目标展示顺序（小在前） */
    @NotEmpty(message = "请提交小节排序")
    private List<Long> ids;
}
