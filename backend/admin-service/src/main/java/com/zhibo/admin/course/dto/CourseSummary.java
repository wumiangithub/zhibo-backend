package com.zhibo.admin.course.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class CourseSummary {

    private String id;
    private String title;
    private String coverUrl;
    private String shelfStatus;
    private String riskStatus;
    private Boolean autoList;
    private String listedTime;
    private List<CoursePerson> owners = new ArrayList<>();
    private int contentCount;
    private int entitledUserCount;
    private int totalUserCount;
    private int salesCount;
    private String updatedTime;
    private String createdTime;
    private boolean canList;
    private boolean canUnlist;
    private boolean canDelete;
}
