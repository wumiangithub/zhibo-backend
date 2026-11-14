package com.zhibo.admin.presence.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DevPresenceItemResult {

    private String username;
    private boolean ok;
    /** 成功时返回 mid；失败可空 */
    private String mid;
    /** 失败原因；成功可空 */
    private String error;
}
