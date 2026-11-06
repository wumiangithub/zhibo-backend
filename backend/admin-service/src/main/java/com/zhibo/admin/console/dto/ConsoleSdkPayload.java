package com.zhibo.admin.console.dto;

import lombok.Data;

/**
 * 中控台微吼 JSSDK init 鉴权载荷（与观看 SdkPayload 同形；email 为 {@code z{id}@qkzb.com}）。
 */
@Data
public class ConsoleSdkPayload {

    /** 回显 {@code z{员工id}}；3.9.1 init 不要与 email 同时传。 */
    private String account;
    /** {@code z{员工id}@qkzb.com}，参与签名（与角色 {@code e{id}} 错开）。 */
    private String email;
    private String username;
    /** 无头像为 {@code ""}；仅非空参与签名。 */
    private String head;
    private String webinarId;
    private String appKey;
    private String signedAt;
    private int signType = 0;
    private String sign;
}
