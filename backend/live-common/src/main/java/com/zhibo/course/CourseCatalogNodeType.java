package com.zhibo.course;

/**
 * 课程目录节点类型（D55 整数枚举）。
 * 库列 {@code course_catalog_node.node_type}。
 * 同父展示顺序：子课程 → 章节 → 小节（非数字自然序，查询侧 CASE）。
 */
public final class CourseCatalogNodeType {

    /** 1 章节（容器） */
    public static final int CHAPTER = 1;
    /** 2 子课程（容器，可带封面） */
    public static final int SUBCOURSE = 2;
    /** 3 小节（绑定直播或素材） */
    public static final int LESSON = 3;

    private CourseCatalogNodeType() {
    }

    public static boolean isKnown(int value) {
        return value == CHAPTER || value == SUBCOURSE || value == LESSON;
    }

    public static boolean isContainer(int value) {
        return value == CHAPTER || value == SUBCOURSE;
    }
}
