package com.zhibo.admin.course.dto;

import lombok.Data;

@Data
public class CourseMaterialView {

    private String id;
    private String name;
    private String url;
    private String type;
    private Long fileSize;
    private String mimeType;
}
