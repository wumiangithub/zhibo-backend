package com.zhibo.admin.activity.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ImportAudienceByTagsEstimateResponse {

    /** 所选标签「满足其一」去重后的观众数 */
    private int total;
}
