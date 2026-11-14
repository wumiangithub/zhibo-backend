package com.zhibo.admin.console.dto;

import lombok.Data;

/** {@code PUT /api/v1/activities/console/products/visible/{id}} */
@Data
public class ConsoleProductVisibleRequest {

    /** 十进制字符串；须为本场已绑定 spu */
    private String spuId;

    /** true 显示 / false 隐藏 */
    private Boolean visible;
}
