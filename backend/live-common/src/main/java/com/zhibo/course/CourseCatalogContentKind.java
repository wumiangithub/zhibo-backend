package com.zhibo.course;

/**
 * 课程小节内容类型（D55 整数枚举）。
 * 库列 {@code course_catalog_node.content_kind}；仅 {@code node_type=小节} 有值。
 */
public final class CourseCatalogContentKind {

    /** 1 直播（activity id） */
    public static final int LIVE = 1;
    /** 2 图片素材 */
    public static final int IMAGE = 2;
    /** 3 视频素材 */
    public static final int VIDEO = 3;
    /** 4 文档素材 */
    public static final int DOCUMENT = 4;
    /** 5 文章素材 */
    public static final int ARTICLE = 5;

    private CourseCatalogContentKind() {
    }

    public static boolean isKnown(int value) {
        return value == LIVE
                || value == IMAGE
                || value == VIDEO
                || value == DOCUMENT
                || value == ARTICLE;
    }

    public static boolean isMaterial(int value) {
        return value == IMAGE || value == VIDEO || value == DOCUMENT || value == ARTICLE;
    }
}
