package com.zhibo.admin.course.dto;

import lombok.Data;

/**
 * 对外课程列表项。
 * {@code departmentName} 取自归属人（{@code sort_order} 最小的第一人）的员工部门；
 * 无归属人或无部门时为 null，由全局 Jackson {@code non_null} 省略。
 */
@Data
public class OutsideCourseItem {

    /** 课程 UUID（D74：32 位无横线）。 */
    private String courseId;
    private String courseName;
    private int contentCount;
    /** 课程部门 = 归属人部门（多部门顿号连接）。 */
    private String departmentName;
}
