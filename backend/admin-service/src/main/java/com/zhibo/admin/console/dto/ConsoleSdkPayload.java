package com.zhibo.admin.console.dto;

import lombok.Data;

/**
 * 中控台微吼 JSSDK init 鉴权载荷（与观看 SdkPayload 同形；email 为 {@code z{id}@qkzb.com}）。
 */
@Data
public class ConsoleSdkPayload {

    /**
     * 第三方用户唯一标识 = {@code z{员工id}}。本站回显；init 以 email 为准。
     * 聊天回包未必带回，认人不能只靠它（D111）。
     */
    private String account;
    /** {@code z{员工id}@qkzb.com}，参与签名（与角色 {@code e{id}} 错开）。 */
    private String email;
    private String username;
    /** 有无真实头像都拼/覆盖 {@code ?v=z{员工id}}（无头像用默认底座，D111）；仅非空参与签名。 */
    private String head;
    private String webinarId;
    private String appKey;
    private String signedAt;
    private int signType = 0;
    private String sign;
}
