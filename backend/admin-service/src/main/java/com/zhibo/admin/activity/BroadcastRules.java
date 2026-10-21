package com.zhibo.admin.activity;

import com.zhibo.activity.ActivityReady;

/**
 * 开播门禁：活动就绪、风控已通过、已上架，且直播状态为预告或已结束。
 */
public final class BroadcastRules {

    private BroadcastRules() {
    }

    public static boolean canBroadcast(String riskStatus, String shelfStatus, Integer state) {
        return canBroadcast(ActivityReady.OK, riskStatus, shelfStatus, state);
    }

    public static boolean canBroadcast(
            Integer ready, String riskStatus, String shelfStatus, Integer state) {
        return ActivityReady.isOk(ready)
                && "passed".equals(riskStatus)
                && "on".equals(shelfStatus)
                && state != null
                && (state == 2 || state == 3);
    }
}
