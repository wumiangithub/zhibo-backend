package com.zhibo.admin.activity.dto;

import lombok.Data;

/** 打开邀约链接后的加入结果。 */
@Data
public class AcceptActivityMemberResponse {

    /** 被加入的员工，即当前登录人 */
    private Long id;
    private String userName;
    /** 1 主播 / 2 嘉宾 / 3 助理（与微吼 type 一致） */
    private Integer role;
    /** 打开前已经是该角色 */
    private boolean alreadyMember;
    /** 邀约人姓名 */
    private String inviterName;
}
