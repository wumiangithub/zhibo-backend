package com.zhibo.admin.viewertag.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ViewerTagItem {

    private Long id;
    private String name;
    /** 1 手动 / 2 自动 */
    private int type;
    private String definitionSummary;
    private long userCount;
    private Long groupId;
    private String groupName;
    private String createdTime;
}
