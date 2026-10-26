package com.zhibo.live.viewer;

/**
 * 观众上传用途。决定对象键前缀，调用方不能自填前缀。
 * 1 头像 / 2 意见反馈截图。
 */
public final class ViewerUploadBiz {

    public static final int AVATAR = 1;
    public static final int FEEDBACK = 2;

    public static final String AVATAR_PREFIX = "viewer/avatar/";
    public static final String FEEDBACK_PREFIX = "viewer/feedback/";

    private ViewerUploadBiz() {
    }

    public static String prefix(int biz) {
        if (biz == AVATAR) {
            return AVATAR_PREFIX;
        }
        if (biz == FEEDBACK) {
            return FEEDBACK_PREFIX;
        }
        throw new ViewerAuthException(400, "上传用途无效");
    }

    public static boolean isRegistered(String objectKey) {
        return objectKey != null
                && (objectKey.startsWith(AVATAR_PREFIX) || objectKey.startsWith(FEEDBACK_PREFIX));
    }
}
