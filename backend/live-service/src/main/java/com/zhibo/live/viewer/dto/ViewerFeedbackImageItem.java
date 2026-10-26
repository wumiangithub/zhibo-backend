package com.zhibo.live.viewer.dto;

import lombok.Data;

/** 反馈截图一项：访问地址 + TOS 对象键成对。 */
@Data
public class ViewerFeedbackImageItem {

    /** 访问 URL（CDN 或原生桶）。 */
    private String url;

    /** 须以 viewer/feedback/ 开头。 */
    private String objectKey;
}
