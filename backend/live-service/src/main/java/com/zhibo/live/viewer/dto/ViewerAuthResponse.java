package com.zhibo.live.viewer.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

/** 登录和刷新成功时的 data。 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ViewerAuthResponse {

    private String accessToken;
    private int expiresIn;
    private String refreshToken;
    private int refreshExpiresIn;
    private ViewerUserResponse user;
}
