package com.zhibo.admin.auth.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class RefreshResponse {

    private String accessToken;
    private int expiresIn;
    private String refreshToken;
    private int refreshExpiresIn;
}
