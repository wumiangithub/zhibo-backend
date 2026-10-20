package com.zhibo.admin.course.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class OutsideCourseListResponse {

    private int page;
    private int pageSize;
    private long total;
    private List<OutsideCourseItem> list = new ArrayList<>();
}
