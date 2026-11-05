package com.zhibo.activity;

/** {@code room_comment.comment_type}。 */
public final class RoomCommentType {

    /** 聊天。 */
    public static final int CHAT = 1;
    /** 提问。 */
    public static final int QUESTION = 2;

    private RoomCommentType() {
    }

    public static boolean isKnown(Integer type) {
        return type != null && (type == CHAT || type == QUESTION);
    }
}
