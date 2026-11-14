package com.zhibo.admin.console.dto;

import java.util.ArrayList;
import java.util.List;
import lombok.Data;

@Data
public class ConsoleAudienceDetailListResponse {

    private int page;
    private int pageSize;
    private long total;
    private List<ConsoleAudienceDetailItem> list = new ArrayList<>();
}
