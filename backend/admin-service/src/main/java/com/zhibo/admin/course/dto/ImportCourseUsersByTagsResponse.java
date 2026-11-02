package com.zhibo.admin.course.dto;

import lombok.Data;

@Data
public class ImportCourseUsersByTagsResponse {

    /** 命中标签的去重观众数 */
    private int total;
    private int created;
    /** 本课已有且补写了导入人 */
    private int updated;
    /** 本课已有且导入人已有，跳过 */
    private int skipped;
}
