package com.zhibo.admin.console.dto;

import java.util.ArrayList;
import java.util.List;
import lombok.Data;

@Data
public class ConsoleCommerceDetailListResponse {

    private int page;
    private int pageSize;
    private long total;
    private List<ConsoleCommerceDetailItem> list = new ArrayList<>();
}
