package com.zhibo.admin.dashboard.dto;

import java.util.ArrayList;
import java.util.List;
import lombok.Data;

/** 工作台「最近直播数据」分页列表。 */
@Data
public class RecentLivesResponse {

    private int page;
    private int pageSize;
    private long total;
    private List<RecentLiveRowResponse> list = new ArrayList<>();
}
