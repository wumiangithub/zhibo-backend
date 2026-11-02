package com.zhibo.admin.course.dto;

import lombok.Data;

import java.util.List;

@Data
public class BindMaterialsResponse {

    private List<CatalogNodeView> list;
    /** 课程当前未删小节数 */
    private Integer contentCount;
}
