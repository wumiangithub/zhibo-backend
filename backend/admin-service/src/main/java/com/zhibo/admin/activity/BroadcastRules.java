package com.zhibo.admin.activity;

/**
 * 开播门禁：风控已通过、已上架，且直播状态为预告或已结束。
 */
public final class BroadcastRules {

    private BroadcastRules() {
    }

    public static boolean canBroadcast(String riskStatus, String shelfStatus, Integer state) {
        return "passed".equals(riskStatus)
                && "on".equals(shelfStatus)
                && state != null
                && (state == 2 || state == 3);
    }
}
