package com.zhibo.admin.course.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class CourseUserEntitlementResponse {

    private Long id;
    private int entitlementStatus;
}
