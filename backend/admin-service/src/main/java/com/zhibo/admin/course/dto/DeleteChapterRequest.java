package com.zhibo.admin.course.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class DeleteChapterRequest {

    @NotNull(message = "章节 id 不能为空")
    private Long id;

    /**
     * true：保留内容——无 {@code targetChapterId} 时仅软删章节节点（小节 parent 不动）；
     * 有 {@code targetChapterId} 时先把直属小节迁到目标章节，再软删本节点；
     * false：级联软删其下小节（学员不可再看）。
     */
    @NotNull(message = "请选择删除方式")
    private Boolean keepContents;

    /**
     * 可选。仅 {@code keepContents=true} 时生效：把本章节下直属小节的 parent 改到该章节。
     * 目标须为本课未删章节、与删除章节同 parentId、且 ≠ id。
     */
    private Long targetChapterId;
}
