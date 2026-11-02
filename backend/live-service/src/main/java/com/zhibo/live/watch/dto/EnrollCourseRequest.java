package com.zhibo.live.watch.dto;

import lombok.Data;

/**
 * 课程分享链 enroll。被邀请观众 id 只从 Authorization 观众 JWT 取，不进 Body。
 * {@code inviterId} 可选：有则写员工邀请（空则填、不覆盖）；无则仅 upsert 学员行。
 */
@Data
public class EnrollCourseRequest {

    /** 员工邀请人 {@code rs_mainkey_id}；省略或无效则不写邀请字段。 */
    private Long inviterId;
}
