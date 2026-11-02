package com.zhibo.admin.course.dto;

import java.util.ArrayList;
import java.util.List;
import lombok.Data;

@Data
public class CourseUserImportHistoryListResponse {

    private List<CourseUserImportHistoryItem> list = new ArrayList<>();
}
