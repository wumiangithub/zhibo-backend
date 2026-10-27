package com.zhibo.admin.activity.dto;

import java.util.ArrayList;
import java.util.List;
import lombok.Data;

@Data
public class AudienceListResponse {

    private int page;
    private int pageSize;
    private long total;
    private List<AudienceListItem> list = new ArrayList<>();
}
