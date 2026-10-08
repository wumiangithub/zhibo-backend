package com.zhibo.live.watch.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class LeaveAttendanceItem {

    @NotBlank(message = "userId 不能为空")
    @Size(max = 128, message = "userId 不能超过128个字")
    private String userId;

    @NotBlank(message = "enterTime 不能为空")
    private String enterTime;

    @NotBlank(message = "leaveTime 不能为空")
    private String leaveTime;

    /** 微吼 SDK / 回调消息 id；有则幂等。可空（兼容旧前端）。 */
    @Size(max = 64, message = "mid 不能超过64个字")
    private String mid;
}
