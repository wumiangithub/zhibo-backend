package com.zhibo.admin.auth.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class RefreshRequest {

    /** 旧 access。过期也要原样带上。 */
    @NotBlank(message = "accessToken 不能为空")
    private String accessToken;

    @NotBlank(message = "refreshToken 不能为空")
    private String refreshToken;
}
