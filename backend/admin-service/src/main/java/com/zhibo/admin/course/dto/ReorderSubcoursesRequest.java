package com.zhibo.admin.course.dto;

import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

@Data
public class ReorderSubcoursesRequest {

    /** 本课全部子课程 id，按目标展示顺序（小在前） */
    @NotEmpty(message = "请提交子课程排序")
    private List<Long> ids;
}
