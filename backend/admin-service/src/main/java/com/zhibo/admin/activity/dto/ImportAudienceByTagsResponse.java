package com.zhibo.admin.activity.dto;

import lombok.Data;

@Data
public class ImportAudienceByTagsResponse {

    /** 命中标签的去重观众数 */
    private int total;
    private int created;
    /** 本场已有且补写了导入人 */
    private int updated;
    /** 本场已有且导入人已有，跳过 */
    private int skipped;
}
