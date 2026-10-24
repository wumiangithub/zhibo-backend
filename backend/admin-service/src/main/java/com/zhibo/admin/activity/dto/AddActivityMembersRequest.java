package com.zhibo.admin.activity.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

/**
 * 增量添加开播成员。只插入请求里的人，不删除、不改已有成员。
 */
@Data
public class AddActivityMembersRequest {

    @NotNull(message = "members 不能为空")
    @Valid
    private List<MemberBinding> members;

    @Data
    public static class MemberBinding {
        /** 员工 rs_mainkey_id */
        private Long id;
        /** 1 主播 / 2 嘉宾 / 3 助理（与微吼 type 一致） */
        private Integer role;
        /** 本场昵称 ≤10；空则快照员工姓名 */
        private String nickname;
        /** 本场头像；空则快照员工头像 */
        private String avatarUrl;
        /** 标签；空则用角色默认标签 */
        private String tag;
    }
}
