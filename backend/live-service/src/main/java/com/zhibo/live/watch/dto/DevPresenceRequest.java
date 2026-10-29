package com.zhibo.live.watch.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** 本地模拟微吼上下线回调。仅 profile=local 的接口使用。 */
@Data
public class DevPresenceRequest {

    @NotBlank(message = "type 不能为空")
    @Pattern(regexp = "Join|Leave", message = "type 只能是 Join 或 Leave")
    private String type;

    @NotBlank(message = "email 不能为空")
    @Size(max = 64, message = "email 不能超过64个字")
    private String email;

    @NotBlank(message = "socketId 不能为空")
    @Size(max = 64, message = "socketId 不能超过64个字")
    private String socketId;

    @Size(max = 128, message = "nickname 不能超过128个字")
    private String nickname;
}
