package com.zhibo.admin.console.dto;

import lombok.Data;

/** {@code PUT /api/v1/activities/console/products/visible/{id}} 成功 data */
@Data
public class ConsoleProductVisibleResponse {

    private String id;
    private String spuId;
    private boolean visible;
}
