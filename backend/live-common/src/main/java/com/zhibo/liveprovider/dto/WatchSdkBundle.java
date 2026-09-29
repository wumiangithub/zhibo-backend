package com.zhibo.liveprovider.dto;

import lombok.Data;

@Data
public class WatchSdkBundle {

    private String title;
    private Integer state;
    private Integer type;
    private String startTime;
    private String endTime;
    private String embedUrl;
    private SdkInit sdk;

    @Data
    public static class SdkInit {
        private String scriptUrl;
        private String jqueryUrl;
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
