package com.zhibo.live.viewer.dto;

import java.util.ArrayList;
import java.util.List;
import lombok.Data;

@Data
public class ViewerLearningCourseListResponse {

    private Long total;
    private List<ViewerLearningCourseItem> list = new ArrayList<>();
}
