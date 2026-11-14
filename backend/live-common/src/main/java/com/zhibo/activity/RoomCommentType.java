package com.zhibo.activity;

/** {@code room_comment.comment_type}。 */
public final class RoomCommentType {

    /** 聊天。 */
    public static final int CHAT = 1;
    /** 提问。 */
    public static final int QUESTION = 2;
    /** 优惠券（中控台发送；content 为 JSON；D113）。 */
    public static final int COUPON = 3;
    /** 预留：图片消息（微吼图聊 JSON 快照含 image_urls；本轮不写）。 */
    public static final int IMAGE = 4;
    /** 系统消息（中控台发送；content 为 JSON：text + pin；D127）。 */
    public static final int SYSTEM = 5;

    private RoomCommentType() {
    }

    /** 前端 DEV 上报仅允许聊天/提问。 */
    public static boolean isKnown(Integer type) {
        return type != null && (type == CHAT || type == QUESTION);
    }

    /** 用户管理「评论次数」计入的类型。 */
    public static boolean countsTowardCommentCount(Integer type) {
        return type != null && (type == CHAT || type == QUESTION);
    }
}
