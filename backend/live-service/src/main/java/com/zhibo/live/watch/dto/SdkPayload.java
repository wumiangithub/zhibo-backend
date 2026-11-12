package com.zhibo.live.watch.dto;

import lombok.Data;

/**
 * 微吼 JS SDK 3.9.1 init 鉴权载荷（前端 {@code new VhallSDK(...)}）。
 * <p>
 * 字段名按 {@code API约定.md} camelCase 下发；签名参与字段见 {@link com.zhibo.live.watch.WatchSdkService}。
 * secret 不在此对象，绝不返回前端。脚本 CDN 由前端适配器加载（D93），本对象不含 {@code scriptUrl}。
 * <p>
 * 3.9.1：{@code email} 与 {@code account} 互斥；本项目只用 {@code email}。
 */
@Data
public class SdkPayload {

    /**
     * 第三方用户唯一标识（观众 = {@code viewer.username}）。本站回显；
     * 微吼 3.9.1 init 以 email 为准，<strong>不要</strong>与 email 同时传冲突字段。
     * 聊天回包未必带回，认人不能只靠它（见 D111 / chatViewerId）。
     */
    private String account;
    /** {@code {username}@qkzb.com}，参会身份并参与签名（D92 / D118）。 */
    private String email;
    /** 昵称。 */
    private String username;
    /**
     * 观众头像 URL，对应 JSSDK {@code head}；仅非空参与签名。
     * 有无真实头像都拼/覆盖 {@code ?v={username}}（无头像用默认底座，D111 / D118）。
     */
    private String head;
    /** 活动 id（SDK 参数名 webinar_id）。 */
    private String webinarId;
    /** 环境变量 AppKey（可公开下发）。 */
    private String appKey;
    /** Unix 秒字符串，对应 SDK 参数名 signedat（约 5 分钟有效）。 */
    private String signedAt;
    /** 签名类型：0=MD5。不参与签名计算。 */
    private int signType = 0;
    /** 服务端用 SecretKey MD5 生成，32 位小写 hex。 */
    private String sign;
}
