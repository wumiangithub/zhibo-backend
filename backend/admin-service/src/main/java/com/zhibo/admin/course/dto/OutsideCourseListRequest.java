package com.zhibo.admin.course.dto;

import lombok.Data;

/** 对外课程列表请求体。可空；缺省等价于第 1 页、每页 10 条、启用+已下架。 */
@Data
public class OutsideCourseListRequest {

    /** 页码，从 1 开始；缺省 1；小于 1 按 1。 */
    private Integer page;

    /** 每页条数；缺省 10，最大 100（超出按 100，不抛 400）。 */
    private Integer pageSize;

    /** 课程名称模糊搜索，不超过 50 字符；空或不传则不过滤。超长 → 400。 */
    private String name;

    /** 归属部门 ID，精确过滤课程第一归属人的部门。 */
    private Long deptId;

    /** 状态：1 启用 2 已下架；不传查全部（启用+已下架）。非法值 → 400。 */
    private Integer status;
}
