package com.zhibo.live.volc;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 火山 TOS（观众头像上传）。绑定 {@code volc.*}，与管理端共用 {@code VOLC_*}。
 * 缺凭证不拒启动；上传时再校验。
 */
@Data
@ConfigurationProperties(prefix = "volc")
public class VolcTosProperties {

    private String accessKey = "";

    private String secretKey = "";

    private String tosRegion = "cn-shanghai";

    /** 主机名，不含 https://。 */
    private String tosEndpoint = "tos-cn-shanghai.volces.com";

    private String tosBucket = "qky-live-sucai-test";

    /** 落库访问前缀，如 https://qklive-testcos.cjs99.com；空则拼原生桶域名。 */
    private String tosCdnUrl = "";

    /** AssumeRole 角色 TRN；STS 直传需要。 */
    private String stsRoleTrn = "trn:iam::2116729154:role/sts";

    private int stsDurationSeconds = 3600;

    public boolean credentialsMissing() {
        return isBlank(accessKey) || isBlank(secretKey) || isBlank(tosRegion)
                || isBlank(tosEndpoint) || isBlank(tosBucket);
    }

    /** STS 比 PutObject 多要角色 TRN。 */
    public boolean stsCredentialsMissing() {
        return credentialsMissing() || isBlank(stsRoleTrn);
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
