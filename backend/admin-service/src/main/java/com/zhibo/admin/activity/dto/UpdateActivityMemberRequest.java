package com.zhibo.admin.activity.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 更新一名已有开播成员的本场昵称、头像、标签。不改角色，不增删其他人。
 */
@Data
public class UpdateActivityMemberRequest {

    /** 员工 rs_mainkey_id */
    @NotNull(message = "成员不能为空")
    private Long id;

    /** 本场昵称 ≤10；空则快照员工姓名 */
    private String nickname;

    /** 本场头像；空则快照员工头像 */
    private String avatarUrl;

    /** 标签 ≤32；空则用该角色默认标签 */
    private String tag;
}
