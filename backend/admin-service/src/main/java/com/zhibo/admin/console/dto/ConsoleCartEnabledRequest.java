package com.zhibo.admin.console.dto;

import lombok.Data;

/** {@code PUT /api/v1/activities/console/cart/{id}} */
@Data
public class ConsoleCartEnabledRequest {

    /** true 开小黄车 / false 关 */
    private Boolean enabled;
}
