package com.zhibo.live.viewer.dto;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 反馈列表分页。 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ViewerFeedbackListResponse {

    private long total;
    private int page;
    private int pageSize;
    private List<ViewerFeedbackListItem> list;
}
