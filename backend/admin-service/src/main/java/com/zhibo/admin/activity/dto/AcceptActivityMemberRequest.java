package com.zhibo.admin.activity.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/** 打开角色邀约链接时提交。加入的人是当前登录员工，不是邀约人。 */
@Data
public class AcceptActivityMemberRequest {

    /** 1 主播 / 2 嘉宾 / 3 助理（与微吼 type 一致） */
    @NotNull(message = "邀约角色不能为空")
    private Integer role;

    /** 发出邀约的员工 rs_mainkey_id */
    @NotNull(message = "邀约人不能为空")
    private Long inviterId;
}
