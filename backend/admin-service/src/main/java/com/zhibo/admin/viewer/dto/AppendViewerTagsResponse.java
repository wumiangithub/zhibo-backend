package com.zhibo.admin.viewer.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AppendViewerTagsResponse {

    /** 实际有新增标签关系的用户数 */
    private int updated;
}
