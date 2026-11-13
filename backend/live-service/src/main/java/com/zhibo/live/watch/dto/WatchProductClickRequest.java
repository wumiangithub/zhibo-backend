package com.zhibo.live.watch.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/** {@code POST /api/v1/watch/products/click/{id}} 请求体。 */
@Data
public class WatchProductClickRequest {

    @NotBlank(message = "spuId 不能为空")
    private String spuId;
}
