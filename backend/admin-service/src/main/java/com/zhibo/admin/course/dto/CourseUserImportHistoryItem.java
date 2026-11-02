package com.zhibo.admin.course.dto;

import lombok.Data;

@Data
public class CourseUserImportHistoryItem {

    private Long id;
    private String importTime;
    private Long importerId;
    private String importerName;
    private int successCount;
    private int failCount;
}
