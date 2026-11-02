package com.zhibo.admin.course.dto;

import java.util.ArrayList;
import java.util.List;
import lombok.Data;

@Data
public class CourseUserImportResponse {

    private Long importId;
    private int total;
    private int created;
    private int updated;
    private int skipped;
    private int failed;
    private List<ErrorItem> errors = new ArrayList<>();

    @Data
    public static class ErrorItem {
        private int row;
        private String phone;
        private String reason;
    }
}
