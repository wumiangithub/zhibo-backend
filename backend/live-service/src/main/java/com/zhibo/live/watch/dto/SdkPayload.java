package com.zhibo.live.watch.dto;

import lombok.Data;

/**
 * 微吼 JS SDK 3.9.1 初始化载荷（前端 {@code new VhallSDK(...)}）。
 * <p>
 * 字段名按 {@code API约定.md} camelCase 下发；签名参与字段名见 {@link com.zhibo.live.watch.WatchSdkService}。
 * secret 不在此对象，绝不返回前端。
 * <p>
 * 3.9.1：{@code email} 与 {@code account} 互斥；本项目只用 {@code email}。
 */
@Data
public class SdkPayload {

    /** VhallSDK.js 官方 CDN（3.9.1）。 */
    private String scriptUrl;
    /**
     * 旧 2.x 依赖 jQuery；3.9.1 文档未要求。可空，前端有值才加载。
     */
    private String jqueryUrl;
    /**
     * 第三方用户 id（仅本站 guestId 回显；<strong>不要</strong>传给 3.9.1 init，与 email 互斥）。
     */
    private String account;
    /** {guestId}@zhibo.local，参会身份（推荐，参与签名）。 */
    private String email;
    /** 昵称。 */
    private String username;
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
