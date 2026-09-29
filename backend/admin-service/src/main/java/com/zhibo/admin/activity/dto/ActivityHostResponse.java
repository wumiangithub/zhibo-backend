package com.zhibo.admin.activity.dto;

import lombok.Data;

@Data
public class ActivityHostResponse {

    private String provider;
    /** 主持人页，新窗口打开；有效期约 7 天（微吼） */
    private String hostUrl;
}
