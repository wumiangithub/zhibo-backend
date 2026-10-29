package com.zhibo.live.watch.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 本地模拟回调已交给正式处理链。{@code mid} 与本次回调幂等键相同。 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DevPresenceResponse {

    private String mid;
}
