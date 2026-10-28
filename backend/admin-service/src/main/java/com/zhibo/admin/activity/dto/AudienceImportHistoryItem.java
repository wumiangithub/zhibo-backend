package com.zhibo.admin.activity.dto;

import lombok.Data;

@Data
public class AudienceImportHistoryItem {

    private Long id;
    private String importTime;
    private Long importerId;
    private String importerName;
    private int successCount;
    private int failCount;
}
