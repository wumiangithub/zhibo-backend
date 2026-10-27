package com.zhibo.viewer;

/** 观众标签类型（D55 / D80）。 */
public final class ViewerTagType {

    /** 手动标签 */
    public static final int MANUAL = 1;
    /** 自动标签 */
    public static final int AUTO = 2;

    private ViewerTagType() {}

    public static boolean isValid(int type) {
        return type == MANUAL || type == AUTO;
    }
}
