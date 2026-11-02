package com.zhibo.admin.activity.dto;

import java.util.List;
import lombok.Data;

@Data
public class ImportAudienceByTagsRequest {

    /** 标签 id，1～5 个；「满足其一」匹配 */
    private List<Long> tagIds;
}
