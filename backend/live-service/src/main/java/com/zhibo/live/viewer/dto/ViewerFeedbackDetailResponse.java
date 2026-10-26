package com.zhibo.live.viewer.dto;

import java.util.List;
import lombok.Data;

/** 反馈详情。 */
@Data
public class ViewerFeedbackDetailResponse {

    private Long id;
    private int category;
    private String categoryLabel;
    private String content;
    /** 选填；未填则省略或空。 */
    private String contactPhone;
    private List<ViewerFeedbackImageItem> images;
    /** yyyy-MM-dd HH:mm:ss */
    private String createdTime;
}
