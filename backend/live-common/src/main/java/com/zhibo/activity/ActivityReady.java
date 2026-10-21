package com.zhibo.activity;

/**
 * {@link ActivityEntity#getReady()} 取值。
 */
public final class ActivityReady {

    /** 未就绪：录播视频转码中 / 尚未 create-demand。 */
    public static final int PENDING = 0;
    /** 就绪：云侧房间已建好。 */
    public static final int OK = 1;
    /** 失败（转码或 create-demand）；本版不可重试。 */
    public static final int FAIL = 2;

    private ActivityReady() {
    }

    public static boolean isOk(Integer ready) {
        return ready != null && ready == OK;
    }
}
