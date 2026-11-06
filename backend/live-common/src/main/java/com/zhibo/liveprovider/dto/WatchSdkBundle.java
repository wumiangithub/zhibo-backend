package com.zhibo.liveprovider.dto;

import lombok.Data;

@Data
public class WatchSdkBundle {

    /** 仅签名/init；活动标题/态/时间由本库 Activity 填（D94）。 */
    private SdkInit sdk;

    /** 签名与 init 鉴权字段；脚本 CDN 由前端适配器加载（D93）。 */
    @Data
    public static class SdkInit {
        private String account;
        private String email;
        private String username;
        /** 观众头像 URL；无头像为 {@code ""}。对应 JSSDK {@code head}；仅非空参与签名。 */
        private String head;
        private String webinarId;
        private String appKey;
        private String signedAt;
        /** 0=MD5 */
        private Integer signType;
        private String sign;
    }
}
