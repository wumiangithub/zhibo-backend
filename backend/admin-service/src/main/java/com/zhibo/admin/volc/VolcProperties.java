package com.zhibo.admin.volc;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 火山引擎 TOS / STS 配置。
 * <p>
 * 绑定 {@code volc.*}（.env / .env.local 的 {@code VOLC_*} 经 dotenv 注入）。
 * 缺凭证时不拒绝启动；调用 STS 接口时再校验。
 */
@Data
@ConfigurationProperties(prefix = "volc")
public class VolcProperties {

    private String accessKey = "";

    private String secretKey = "";

    /** 可被 Assume 的角色 TRN。 */
    private String stsRoleTrn = "trn:iam::2116729154:role/sts";

    private String tosRegion = "cn-shanghai";

    /** 主机名，不含 {@code https://}。 */
    private String tosEndpoint = "tos-cn-shanghai.volces.com";

    private String tosBucket = "qky-live-sucai-test";

    /** 临时凭证有效秒数；契约建议 900～3600。 */
    private int stsDurationSeconds = 3600;

    public boolean credentialsMissing() {
        return isBlank(accessKey) || isBlank(secretKey) || isBlank(stsRoleTrn)
                || isBlank(tosRegion) || isBlank(tosEndpoint) || isBlank(tosBucket);
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
