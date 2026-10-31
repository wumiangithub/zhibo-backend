package com.zhibo.admin.course.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

@Data
public class ReorderCatalogRequest {

    /** 当前目录上下文：根 0，或子课程 id */
    @NotNull(message = "请提交父节点")
    private Long parentId;

    /** {@code chapters} | {@code lessons} */
    @NotBlank(message = "请提交排序模式")
    private String mode;

    /**
     * 展示顺序。
     * <ul>
     *   <li>{@code chapters}：该上下文下全部直挂章节（不含小节），parentId 均须为上下文 parentId</li>
     *   <li>{@code lessons}：先序：直挂章节/小节交错，章内小节紧随其章节且 parentId=章节 id</li>
     * </ul>
     */
    @NotEmpty(message = "请提交排序项")
    @Valid
    private List<ReorderCatalogItem> items;
}
