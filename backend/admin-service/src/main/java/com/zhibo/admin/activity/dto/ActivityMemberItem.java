package com.zhibo.admin.activity.dto;

import lombok.Data;

/**
 * 开播成员一行。
 * {@code role}：1 主播 / 2 嘉宾 / 3 助理（与微吼 type 一致；{@link com.zhibo.activity.ActivityMemberRole}）。
 */
@Data
public class ActivityMemberItem {

    /** 员工 rs_mainkey_id */
    private Long id;
    private String userName;
    /** 任职部门展示名；无则空串 */
    private String departmentName;
    /** 1 主播 / 2 嘉宾 / 3 助理 */
    private Integer role;
    /** 本场昵称（写入 ≤10）；库空时回员工姓名 */
    private String nickname;
    /** 本场头像；库空时可不输出 */
    private String avatarUrl;
    /** 标签；库空时回空串 */
    private String tag;
}
