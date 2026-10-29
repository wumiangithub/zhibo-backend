package com.zhibo.live.watch.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ReplayHeartbeatRequest {

    @NotBlank(message = "socketId 不能为空")
    @Size(max = 32, message = "socketId 无效")
    private String socketId;
}
