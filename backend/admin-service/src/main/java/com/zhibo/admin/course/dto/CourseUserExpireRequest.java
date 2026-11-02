package com.zhibo.admin.course.dto;

import lombok.Data;

@Data
public class CourseUserExpireRequest {

    /** yyyy-MM-dd HH:mm:ss */
    private String expireTime;
}
