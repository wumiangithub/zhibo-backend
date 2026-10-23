package com.zhibo.live.viewer.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class WechatLoginRequest {

    /** wx.login 的 code。 */
    @NotBlank(message = "缺少微信登录凭证")
    private String code;

    /** 必须勾选用户协议和隐私协议。 */
    @AssertTrue(message = "请先阅读并同意用户协议和隐私协议")
    private boolean agreed;
}
