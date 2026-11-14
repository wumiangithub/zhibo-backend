package com.zhibo.admin.attendance.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AttendeeItem {

    /** 邮箱本地部分：观众 username / e{员工id} / z{员工id}（库列 username）。 */
    private String username;

    private String nickname;
    private String avatarUrl;
    private String enterTime;
    private String leaveTime;
    private boolean online;

    /** 助教侧角色角标：讲师 / 嘉宾 / 助教 / 中控；学员侧不返回。 */
    private String roleLabel;

    /** 学员跟进人（仅邀请人姓名）；无则省略。助教侧不返回。 */
    private String followerName;

    /** 本场未软删聊天+提问条数（comment_type 1/2）；学员侧恒有；助教侧可省略。 */
    private Integer commentCount;
}
