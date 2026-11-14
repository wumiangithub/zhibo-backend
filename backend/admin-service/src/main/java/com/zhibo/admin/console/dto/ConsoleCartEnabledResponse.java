package com.zhibo.admin.console.dto;

import lombok.Data;

/** {@code PUT /api/v1/activities/console/cart/{id}} 成功 data */
@Data
public class ConsoleCartEnabledResponse {

    private String id;
    private boolean cartEnabled;
}
