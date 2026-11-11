package com.zhibo.admin.viewer.dto;

import java.util.ArrayList;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UpdateViewerTagsResponse {

    /** 用户中心 uid */
    private Long uid;
    private List<Long> tagIds = new ArrayList<>();
    private List<String> tagLabels = new ArrayList<>();
}
