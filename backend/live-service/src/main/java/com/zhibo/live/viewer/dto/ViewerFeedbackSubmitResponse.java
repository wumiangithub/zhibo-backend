package com.zhibo.live.viewer.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 提交反馈成功。 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ViewerFeedbackSubmitResponse {

    /** 新建反馈主键。 */
    private Long id;
}
