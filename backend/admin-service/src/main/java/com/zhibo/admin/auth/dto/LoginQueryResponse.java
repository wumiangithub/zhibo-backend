package com.zhibo.admin.auth.dto;

import lombok.Data;

@Data
public class LoginQueryResponse {
    /** 0 pending / 1 success / 2 failed / 3 expired */
    private int status;
    private String errorMsg;
    private String accessToken;
    private Integer expiresIn;
    private String refreshToken;
    private Integer refreshExpiresIn;
    private AuthUserResponse user;
}
