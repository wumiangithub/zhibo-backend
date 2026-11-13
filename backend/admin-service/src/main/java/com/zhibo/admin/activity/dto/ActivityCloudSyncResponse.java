package com.zhibo.admin.activity.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 管理端显式云补偿结果（D120）。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ActivityCloudSyncResponse {

    /** 本站活动 UUID */
    private String id;
    /** 补偿后本库 state（可能刚被 info 写回） */
    private Integer state;
    /** 是否尝试过 webinar/info 对账 */
    private boolean stateAttempted;
    /** info 是否写回了本库 state */
    private boolean stateUpdated;
    /** 是否尝试过 switch-list 场次 sync */
    private boolean sessionsAttempted;
    /** 是否尝试过回放 get-list 补偿（结束类或已有 actual_end 才为 true） */
    private boolean recordsAttempted;
    /** 回放 get-list 是否成功（未尝试则为 false） */
    private boolean recordsOk;
    /** 非 vhall / 无 provider_room_id 时为 false */
    private boolean applicable;
}
