package com.zhibo.viewer;

/** 观众账号状态。库列 {@code viewer.status} 用整数。 */
public final class ViewerStatus {

    /** 正常，可以登录。 */
    public static final int NORMAL = 1;

    /** 停用。登录、刷新、查看当前观众都拒绝。 */
    public static final int DISABLED = 0;

    private ViewerStatus() {
    }

    public static boolean isNormal(int status) {
        return status == NORMAL;
    }
}
