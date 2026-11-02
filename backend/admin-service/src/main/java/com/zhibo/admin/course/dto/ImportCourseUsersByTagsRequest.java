package com.zhibo.admin.course.dto;

import java.util.List;
import lombok.Data;

@Data
public class ImportCourseUsersByTagsRequest {

    /** 标签 id，1～5 个；「满足其一」匹配 */
    private List<Long> tagIds;

    /**
     * 选填。仅作用于新建 {@code course_user} 行；已有行不改 expire。
     * 格式：{@code yyyy-MM-dd}（存当日 00:00:00）或 {@code yyyy-MM-dd HH:mm:ss}。
     * 空 → 按课程售卖 {@code expireAtJoin}。
     */
    private String expireTime;
}
