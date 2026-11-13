package com.zhibo.admin.commerce.dto;

import java.util.ArrayList;
import java.util.List;
import lombok.Data;

/** {@code GET /api/v1/activities/commerce-data/{id}} 响应。 */
@Data
public class ActivityCommerceDataResponse {

    private String id;
    private List<Row> list = new ArrayList<>();

    @Data
    public static class Row {
        private String spuId;
        private String productName;
        private long clickCount;
        private long clickUserCount;
        private long dealUserCount;
        /** 两位小数字符串，如 {@code "26800.00"}。 */
        private String dealAmount;
        /** 本轮恒 {@code "0.00"}。 */
        private String refundAmount;
    }
}
