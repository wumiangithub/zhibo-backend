package com.zhibo.course;

/**
 * 课程课件学员权限（D55 整数枚举）。
 * 库列 {@code course_material.access_permission}。
 */
public final class CourseMaterialAccess {

    /** 1 预览及下载 */
    public static final int PREVIEW_DOWNLOAD = 1;
    /** 2 仅预览 */
    public static final int PREVIEW_ONLY = 2;
    /** 3 仅下载（压缩包等不可预览格式固定） */
    public static final int DOWNLOAD_ONLY = 3;

    private CourseMaterialAccess() {
    }

    public static boolean isKnown(int value) {
        return value == PREVIEW_DOWNLOAD || value == PREVIEW_ONLY || value == DOWNLOAD_ONLY;
    }
}
