package com.zhibo.admin.activity.dto;

import lombok.Data;

/** 详情课件项；id=素材 UUID；name/url/type 为绑定快照。 */
@Data
public class ActivityCoursewareView {

    private String id;
    private String name;
    private String type;
    private String url;
}
