package com.zhibo.admin.course.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class CourseUserExpireResponse {

    private Long id;
    /** yyyy-MM-dd HH:mm:ss */
    private String expireTime;
}
