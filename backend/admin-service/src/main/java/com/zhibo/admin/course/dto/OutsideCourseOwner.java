package com.zhibo.admin.course.dto;

import lombok.Data;

/** 对外课程归属人。只给 id 与姓名，不含头像、部门。 */
@Data
public class OutsideCourseOwner {

    private Long id;
    private String userName;
}
