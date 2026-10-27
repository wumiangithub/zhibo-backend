package com.zhibo.admin.activity.dto;

import java.util.ArrayList;
import java.util.List;
import lombok.Data;

@Data
public class AudienceCommentListResponse {

    private int page;
    private int pageSize;
    private long total;
    private List<AudienceCommentListItem> list = new ArrayList<>();
}
