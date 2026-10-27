package com.zhibo.admin.viewer.dto;

import java.util.ArrayList;
import java.util.List;
import lombok.Data;

@Data
public class ViewerListResponse {

    private int page;
    private int pageSize;
    private long total;
    private List<ViewerListItem> list = new ArrayList<>();
}
