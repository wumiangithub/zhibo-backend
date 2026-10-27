package com.zhibo.live.watch.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class AttributeAudienceRequest {

    @NotNull(message = "viewerId 不能为空")
    private Long viewerId;

    /** 可选；只接受 employee（缺省按员工）。传 audience → 400。 */
    private String inviterKind;

    /** 员工 id */
    @NotNull(message = "inviterId 不能为空")
    private Long inviterId;
}
