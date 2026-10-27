package com.zhibo.admin.activity.dto;

import java.util.ArrayList;
import java.util.List;
import lombok.Data;

@Data
public class AudienceImportResponse {

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
