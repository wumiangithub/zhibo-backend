package com.zhibo.admin.activity.dto;

import lombok.Data;

/**
 * 主持人开播链接（新窗口打开，不要 iframe）。
 */
@Data
public class ActivityHostResponse {

    /** 主持人页，有效期约 7 天 */
    private String hostUrl;
}
