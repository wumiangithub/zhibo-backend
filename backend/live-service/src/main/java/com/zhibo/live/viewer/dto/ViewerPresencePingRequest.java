package com.zhibo.live.viewer.dto;

import lombok.Data;

/** 观众访问心跳。scene 可选。 */
@Data
public class ViewerPresencePingRequest {

    /** miniapp / live；省略不校验。 */
    private String scene;
}
