package com.zhibo.admin.console.dto;

import java.util.ArrayList;
import java.util.List;
import lombok.Data;

@Data
public class ConsoleStaffDetailListResponse {

    private int page;
    private int pageSize;
    private long total;
    private List<ConsoleStaffDetailItem> list = new ArrayList<>();
}
