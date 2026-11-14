package com.zhibo.admin.presence.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/** 管理端本地模拟观众上下线。仅 profile=local 的接口使用。 */
@Data
public class DevPresenceRequest {

    @NotBlank(message = "type 不能为空")
    @Pattern(regexp = "Join|Leave", message = "type 只能是 Join 或 Leave")
    private String type;

    /** 观众 username 列表（1～200）；同一次请求内去重。 */
    @NotEmpty(message = "usernames 不能为空")
    @Size(max = 200, message = "usernames 最多 200 个")
    private List<String> usernames;
}
