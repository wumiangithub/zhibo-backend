package com.zhibo.admin.course.dto;

import lombok.Data;

/** 归属人。id = rs_mainkey_id（JSON 数字）。 */
@Data
public class CoursePerson {

    private Long id;
    private String userName;
    private String avatar;
}
