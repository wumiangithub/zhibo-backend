package com.zhibo.viewer;

/** 意见反馈分类（与小程序单选项顺序一致）。 */
public final class ViewerFeedbackCategory {

    /** 课程权益。 */
    public static final int COURSE_BENEFIT = 1;
    /** 视频播放。 */
    public static final int VIDEO_PLAY = 2;
    /** 学习体验。 */
    public static final int LEARNING = 3;
    /** 其他。 */
    public static final int OTHER = 4;

    private ViewerFeedbackCategory() {}

    /** 是否为已登记分类。 */
    public static boolean isValid(int category) {
        return category >= COURSE_BENEFIT && category <= OTHER;
    }

    /** 展示文案；未知返回空串。 */
    public static String label(int category) {
        return switch (category) {
            case COURSE_BENEFIT -> "课程权益";
            case VIDEO_PLAY -> "视频播放";
            case LEARNING -> "学习体验";
            case OTHER -> "其他";
            default -> "";
        };
    }
}
