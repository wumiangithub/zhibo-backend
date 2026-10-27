package com.zhibo.admin.viewertag.dto;

import java.util.ArrayList;
import java.util.List;
import lombok.Data;

@Data
public class ViewerTagGroupPageResponse {

    private int page;
    private int pageSize;
    private long total;
    private List<ViewerTagGroupPageItem> list = new ArrayList<>();
}
