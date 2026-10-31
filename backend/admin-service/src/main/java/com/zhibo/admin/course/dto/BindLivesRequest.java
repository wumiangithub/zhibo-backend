package com.zhibo.admin.course.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

@Data
public class BindLivesRequest {

    /** 父节点；根下为 0。有子课程后不可为 0 */
    @NotNull(message = "parentId 不能为空")
    private Long parentId;

    @NotEmpty(message = "请选择直播")
    private List<String> activityIds;
}
