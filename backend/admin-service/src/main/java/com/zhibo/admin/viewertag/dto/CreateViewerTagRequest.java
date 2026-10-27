package com.zhibo.admin.viewertag.dto;

import lombok.Data;

@Data
public class CreateViewerTagRequest {

    private String name;
    /** 1 手动 / 2 自动；本轮仅允许 1 */
    private Integer type;
    private Long groupId;
}
