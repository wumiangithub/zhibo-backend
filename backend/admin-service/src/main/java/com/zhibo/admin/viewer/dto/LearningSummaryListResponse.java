package com.zhibo.admin.viewer.dto;

import java.util.ArrayList;
import java.util.List;
import lombok.Data;

@Data
public class LearningSummaryListResponse {

    private int page;
    private int pageSize;
    private long total;
    private List<LearningSummaryListItem> list = new ArrayList<>();
}
