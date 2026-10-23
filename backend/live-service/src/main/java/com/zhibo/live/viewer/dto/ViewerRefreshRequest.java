package com.zhibo.live.viewer.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ViewerRefreshRequest {

    /** 旧 access。过期也要原样带上。 */
    @NotBlank(message = "登录状态已失效，请重新登录")
    private String accessToken;

    @NotBlank(message = "刷新凭证无效或已过期")
    private String refreshToken;
}
