package com.zhibo.live.watch.dto;

import lombok.Data;

/**
 * 课程分享进房登记。被邀请学员 username 只从 Authorization 观众 JWT 取，不进 Body。
 *
 * <p>邀请归因（与直播 attribute 同形，方案 A）：{@code inviterKind} 区分员工/观众；
 * 有邀请时 {@code inviterId} 必传；观众邀请另传邀请人 {@code username}，本轮不落库 → 400。
 */
@Data
public class EnrollCourseRequest {

    /**
     * 邀请人 id。有邀请归因时必传（&gt;0）。
     * employee：员工 {@code rs_mainkey_id}；audience：亦必传（本轮不落库）。
     */
    private Long inviterId;

    /**
     * 邀请人类型。缺省 / 空串 → {@code employee}。
     * {@code employee} 员工邀请 / {@code audience} 观众邀请。
     */
    private String inviterKind;

    /**
     * 邀请人 {@code viewer.username}。仅 {@code inviterKind=audience} 时使用且必填。
     * 不是被邀请学员（学员只从 JWT 取）。
     */
    private String username;
}
