package com.zhibo.live.viewer.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ViewerPresencePingResponse {

    /** yyyy-MM-dd HH:mm:ss */
    private String lastVisitTime;
}
