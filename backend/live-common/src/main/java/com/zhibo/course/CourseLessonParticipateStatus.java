package com.zhibo.course;

/** 小节参与状态（D101）。库列 {@code course_lesson_progress.participate_status}。 */
public final class CourseLessonParticipateStatus {

    /** 0 未开始 */
    public static final int NOT_STARTED = 0;
    /** 1 进行中 */
    public static final int IN_PROGRESS = 1;
    /** 2 已完成 */
    public static final int COMPLETED = 2;

    private CourseLessonParticipateStatus() {
    }

    public static boolean isKnown(int value) {
        return value == NOT_STARTED || value == IN_PROGRESS || value == COMPLETED;
    }
}
