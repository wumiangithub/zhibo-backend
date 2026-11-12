package com.zhibo.admin.auth.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * local / test 环境：仅输入账号即可登录，跳过密码和微信扫码。
 * 生产 / 预发不得开放对应接口。
 */
@Data
public class DevAccountLoginRequest {

    @NotBlank(message = "请输入账号")
    private String userAccount;
}
