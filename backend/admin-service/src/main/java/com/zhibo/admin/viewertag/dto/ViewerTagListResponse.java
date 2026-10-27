package com.zhibo.admin.viewertag.dto;

import java.util.ArrayList;
import java.util.List;
import lombok.Data;

@Data
public class ViewerTagListResponse {

    private int page;
    private int pageSize;
    private long total;
    private List<ViewerTagItem> list = new ArrayList<>();
}
