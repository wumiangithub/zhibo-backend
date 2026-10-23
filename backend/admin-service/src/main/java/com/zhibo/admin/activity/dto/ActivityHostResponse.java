package com.zhibo.admin.activity.dto;

import lombok.Data;

@Data
public class ActivityHostResponse {

    private String provider;
    /**
     * 微吼 get-role-url 的 page_url；本站开播页 iframe 的 src。
     * 有效期约 7 天（微吼）。
     */
    private String hostUrl;
    /** 本次参会角色：1 主播 / 2 嘉宾 / 3 助理（与微吼 type 一致） */
    private Integer role;
}
