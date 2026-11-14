package com.zhibo.live.watch.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class AttributeAudienceRequest {

    /**
     * 邀请人类型：{@code employee} 员工 / {@code audience} 观众。
     * 缺省按员工。观众邀请本站当前不落库（D77），传 {@code audience} → 400。
     */
    private String inviterKind;

    /**
     * 邀请人 id（必传）。
     * {@code employee} → 员工 rs_mainkey_id；{@code audience} → 亦必传（本轮不落库）。
     * 被邀请观众 username 只从 Authorization 观众 JWT 取（D97），不进 Body。
     */
    @NotNull(message = "inviterId 不能为空")
    private Long inviterId;

    /**
     * 邀请人 {@code viewer.username}。仅 {@code inviterKind=audience} 时使用且必填。
     * 不是被邀请观众（被邀请人只从 JWT 取）。与课程 enroll 同形。
     */
    private String username;
}
