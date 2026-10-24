package com.zhibo.admin.activity.dto;

import lombok.Getter;
import lombok.Setter;

/** 口令进房成功：给 iframe / App WebView 用的主播参会地址。 */
@Getter
@Setter
public class PasscodeEnterResponse {

    private String activityId;
    private String provider;
    private String hostUrl;
    /** 固定 1（主播）。 */
    private Integer role;
    private String nickname;
}
