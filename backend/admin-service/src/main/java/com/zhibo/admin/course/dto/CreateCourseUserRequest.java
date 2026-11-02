package com.zhibo.admin.course.dto;

import lombok.Data;

@Data
public class CreateCourseUserRequest {

    /** 与 viewerId 二选一 */
    private String phone;

    /** 与 phone 二选一 */
    private Long viewerId;
}
