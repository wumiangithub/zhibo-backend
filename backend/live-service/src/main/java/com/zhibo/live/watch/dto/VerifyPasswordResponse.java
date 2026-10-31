package com.zhibo.live.watch.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class VerifyPasswordResponse {

    private String accessToken;
    private Integer expiresIn;
}
