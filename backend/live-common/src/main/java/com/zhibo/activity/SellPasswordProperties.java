package com.zhibo.activity;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 售卖加密密码 AES 密钥（与登录口令 AES 分开；admin / live 共用）。
 */
@Data
@ConfigurationProperties(prefix = "activity")
public class SellPasswordProperties {

    /** AES-256 密钥的 Base64（32 字节）；env {@code ACTIVITY_SELL_PASSWORD_AES_KEY_BASE64}。 */
    private String sellPasswordAesKeyBase64 = "";

    public boolean keyMissing() {
        return sellPasswordAesKeyBase64 == null || sellPasswordAesKeyBase64.isBlank();
    }
}
