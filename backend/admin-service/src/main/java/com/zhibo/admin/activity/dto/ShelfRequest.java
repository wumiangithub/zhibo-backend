package com.zhibo.admin.activity.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class ShelfRequest {

    /** true 上架，false 下架。 */
    @NotNull(message = "listed 不能为空")
    private Boolean listed;
}
