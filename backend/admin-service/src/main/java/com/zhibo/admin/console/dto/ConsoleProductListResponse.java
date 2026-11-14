package com.zhibo.admin.console.dto;

import java.util.ArrayList;
import java.util.List;
import lombok.Data;

/** {@code GET /api/v1/activities/console/products/{id}} */
@Data
public class ConsoleProductListResponse {

    private String id;

    /** 小黄车总开关；false 时观看端商品列表为空，中控台列表仍返回全部绑定 */
    private boolean cartEnabled;

    private List<ConsoleProductItem> list = new ArrayList<>();
}
