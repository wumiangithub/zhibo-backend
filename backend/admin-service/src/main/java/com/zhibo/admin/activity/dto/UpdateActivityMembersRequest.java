package com.zhibo.admin.activity.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

@Data
public class UpdateActivityMembersRequest {

    @NotNull(message = "members 不能为空")
    @Valid
    private List<MemberBinding> members;

    @Data
    public static class MemberBinding {
        /** 员工 rs_mainkey_id */
        private Long id;
        /** 1 主播 / 2 助理 / 3 嘉宾 */
        private Integer role;
        /** 本场昵称 ≤10；空 → NULL */
        private String nickname;
        private String avatarUrl;
        private String tag;
    }
}
