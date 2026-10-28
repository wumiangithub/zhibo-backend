package com.zhibo.live.viewer.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 仅 local 环境：明文手机号登录 / 自动注册。
 * 不要在 test / prod 暴露对应接口。
 */
@Data
public class DevPhoneLoginRequest {

    /** 中国大陆手机号：1 开头 11 位纯数字。 */
    @NotBlank(message = "请输入正确的手机号")
    private String phone;

    /** 必须勾选用户协议和隐私协议。 */
    @AssertTrue(message = "请先阅读并同意用户协议和隐私协议")
    private boolean agreed;
}
