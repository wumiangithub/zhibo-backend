package com.zhibo.live.viewer.dto;

import java.util.ArrayList;
import java.util.List;
import lombok.Data;

/** 小程序「我的学习 · 课程详情」。 */
@Data
public class ViewerLearningCourseDetailResponse {

    private String id;
    private String title;
    private String coverUrl;
    private String introduction;
    private String detailHtml;
    private String validityLabel;
    private List<ViewerLearningSubCourseItem> subCourses = new ArrayList<>();
    private List<ViewerLearningCatalogNode> nodes = new ArrayList<>();
}
