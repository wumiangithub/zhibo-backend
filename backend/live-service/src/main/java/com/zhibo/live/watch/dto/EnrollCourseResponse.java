package com.zhibo.live.watch.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class EnrollCourseResponse {

    private Long id;
    private String username;
    private String courseId;
    /** 本次是否新建学员行。 */
    private boolean created;
    private String title;
}
