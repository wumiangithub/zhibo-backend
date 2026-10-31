package com.zhibo.live.watch.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class VerifyPasswordRequest {

    @NotBlank(message = "password 不能为空")
    private String password;
}
