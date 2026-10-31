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
        private String webinarId;
        private String appKey;
        private String signedAt;
        /** 0=MD5 */
        private Integer signType;
        private String sign;
    }
}
