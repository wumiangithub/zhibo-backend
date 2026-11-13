package com.zhibo.notification;

/** 管理端通知类型（D121）；库与 JSON 均为 INT。 */
public final class AdminNotificationType {

    /** 直播状态异常（第一版预留，不接触发） */
    public static final int LIVE_STATUS_ABNORMAL = 1;
    /** 直播报备审核结果 */
    public static final int LIVE_REPORT_AUDIT = 2;
    /** 直播合规审核结果 */
    public static final int LIVE_AUDIT = 3;
    /** 素材审核结果 */
    public static final int MATERIAL_AUDIT = 4;

    private AdminNotificationType() {
    }

    public static boolean isValid(int type) {
        return type >= LIVE_STATUS_ABNORMAL && type <= MATERIAL_AUDIT;
    }
}
