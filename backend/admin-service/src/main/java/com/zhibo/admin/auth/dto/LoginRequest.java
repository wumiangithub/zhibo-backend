package com.zhibo.admin.auth.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class LoginRequest {

    @NotBlank(message = "请输入账号")
    private String userAccount;

    @NotBlank(message = "请输入密码")
    private String password;

    @NotBlank(message = "请输入验证码")
    private String captchaId;

    @NotBlank(message = "请输入验证码")
    private String captchaCode;
}
