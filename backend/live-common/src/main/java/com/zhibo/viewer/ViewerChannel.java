package com.zhibo.viewer;

/** 观众登录渠道。本轮只写入微信小程序。 */
public final class ViewerChannel {

    /** 微信小程序。 */
    public static final int WECHAT_MP = 1;

    /** 微信公众号。只预留，本轮不登录。 */
    public static final int WECHAT_OA = 2;

    /** 手机网页。只预留，本轮不登录。 */
    public static final int WEB = 3;

    /** App。只预留，本轮不登录。 */
    public static final int APP = 4;

    private ViewerChannel() {
    }
}
