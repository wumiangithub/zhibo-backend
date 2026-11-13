package com.zhibo.notification;

/** 通知上的审核结果（D121）；库与 JSON 均为 INT。 */
public final class AdminNotificationAuditResult {

    /** 无（状态异常类） */
    public static final int NONE = 0;
    /** 已通过 */
    public static final int PASSED = 1;
    /** 已驳回 */
    public static final int REJECTED = 2;

    private AdminNotificationAuditResult() {
    }

    public static boolean isFilterValid(int value) {
        return value == NONE || value == PASSED || value == REJECTED;
    }
}
