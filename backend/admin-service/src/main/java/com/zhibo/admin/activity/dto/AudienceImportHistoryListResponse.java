package com.zhibo.admin.activity.dto;

import java.util.ArrayList;
import java.util.List;
import lombok.Data;

@Data
public class AudienceImportHistoryListResponse {

    private List<AudienceImportHistoryItem> list = new ArrayList<>();
}
