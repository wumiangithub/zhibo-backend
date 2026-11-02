package com.zhibo.admin.course.dto;

import lombok.Data;

/** 对外课程列表请求体。可空；缺省等价于第 1 页全部已上架课程。 */
@Data
public class OutsideCourseListRequest {

    /** 页码，从 1 开始；缺省 1。 */
    private Integer page;

    /** 每页条数；缺省 20，最大 100（超出按 100）。 */
    private Integer pageSize;

    /** 课程名称模糊搜索；空或不传则不过滤。 */
    private String courseName;
}
