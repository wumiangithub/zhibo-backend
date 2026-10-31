package com.zhibo.live.watch.dto;

import lombok.Data;

/** {@code GET /api/v1/watch/access/{id}} */
@Data
public class WatchAccessResponse {

    private String id;
    private Boolean sellAlone;
    private String sellType;
    /** ok / need_password / need_login / forbidden / expired */
    private String accessState;
    private Boolean accessExpired;
    private String accessValidityType;
    private Integer accessValidityDays;
}
