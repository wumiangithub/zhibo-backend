package com.zhibo.live.viewer.dto;

import java.util.List;
import lombok.Data;

/** {@code POST /api/v1/viewer/feedback/submit}。 */
@Data
public class ViewerFeedbackSubmitRequest {

    /** 1课程权益 2视频播放 3学习体验 4其他。 */
    private Integer category;

    /** 意见描述，1～200 字。 */
    private String content;

    /** 选填联系手机号。 */
    private String contactPhone;

    /** 相关截图，0～4 张。 */
    private List<ViewerFeedbackImageItem> images;
}
