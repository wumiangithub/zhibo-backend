package com.zhibo.admin.course.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class DeleteSubcourseRequest {

    @NotNull(message = "子课程 id 不能为空")
    private Long id;

    /**
     * true：保留内容——无 {@code targetSubcourseId} 时仅软删子课程节点（子节点 parent 不动）；
     * 有 {@code targetSubcourseId} 时先把直属章节/根小节迁到目标子课程，再软删本节点；
     * false：级联软删其下章节、小节（学员不可再看）。
     */
    @NotNull(message = "请选择删除方式")
    private Boolean keepContents;

    /**
     * 可选。仅 {@code keepContents=true} 时生效：把本子课程下直属章节与根小节的 parent 改到该子课程。
     */
    private Long targetSubcourseId;
}
