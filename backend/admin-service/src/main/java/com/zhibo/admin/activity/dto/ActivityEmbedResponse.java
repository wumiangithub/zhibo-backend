package com.zhibo.admin.activity.dto;

import lombok.Data;

/**
 * 管理后台嵌入地址（iframe；此页不能开播）。
 */
@Data
public class ActivityEmbedResponse {

    private String embedUrl;
}
