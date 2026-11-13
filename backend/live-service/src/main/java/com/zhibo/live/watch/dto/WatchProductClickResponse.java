package com.zhibo.live.watch.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** {@code POST /api/v1/watch/products/click/{id}} 响应。 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class WatchProductClickResponse {

    private boolean ok = true;
}
