package com.zhibo.live.viewer.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class PhoneLoginRequest {

    /** wx.login 的 code，用来换 openid。 */
    @NotBlank(message = "缺少微信登录凭证")
    private String loginCode;

    /** getPhoneNumber 的 detail.code。 */
    @NotBlank(message = "缺少手机号授权凭证")
    private String phoneCode;

    /** 必须勾选用户协议和隐私协议。 */
    @AssertTrue(message = "请先阅读并同意用户协议和隐私协议")
    private boolean agreed;
}
