package com.zhibo.admin.course.dto;

import lombok.Data;

@Data
public class CreateCourseUserRequest {

    /** 与 uid 二选一 */
    private String phone;

    /** 与 phone 二选一 */
    private String username;

    /**
     * 选填。仅新建行时生效；已有行不改 expire。
     * 格式：{@code yyyy-MM-dd}（存当日 00:00:00）或 {@code yyyy-MM-dd HH:mm:ss}。
     * 空 → 按课程售卖 {@code expireAtJoin}。
     */
    private String expireTime;
}
