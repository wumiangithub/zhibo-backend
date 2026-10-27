package com.zhibo.live.watch.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/** {@code GET /api/v1/watch/products/{id}} 响应。 */
@Data
public class WatchProductListResponse {

    /** 本场绑定快照；前端用 spuIds 调订单中心 {@code viewer/product/list/by-ids}。 */
    private List<WatchProductItem> list = new ArrayList<>();
}
