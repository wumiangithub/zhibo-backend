package com.zhibo.vhall.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 微吼开放平台配置。
 * <p>
 * 类比前端的 {@code import.meta.env}：yml / 环境变量会被 Spring 绑到这个对象上，
 * 业务代码读字段即可，不用到处 {@code System.getenv}。
 * {@code vhall.app-key} 这种 kebab-case 会自动映射到 {@code appKey}。
 */
@Data
@ConfigurationProperties(prefix = "vhall")
public class VhallProperties {

    /** API 根地址，默认值写在这里，避免两份 application.yml 各复制一份。 */
    private String baseUrl = "https://saas-open.vhall.com";

    private String appKey = "";

    private String appSecret = "";

    /** 0=MD5（默认），1=RSA，2=SHA256，3=SM3。本阶段只用 MD5。 */
    private int signType = 0;

    /** 微吼要求的 platform 请求头，固定 15。 */
    private int platform = 15;

    private int connectTimeout = 10_000;

    private int readTimeout = 30_000;

    /**
     * 缺凭证时是否拒绝启动。
     * 默认 true（防止空密钥带着服务“假活着”）；local profile 会改成 false，方便无密钥先把进程拉起来。
     */
    private boolean failOnMissingCredentials = true;

    public boolean credentialsMissing() {
        return appKey == null || appKey.isBlank()
                || appSecret == null || appSecret.isBlank();
    }
}
