package com.zhibo.viewer;

/** 观众首次注册渠道。库列 {@code viewer.register_channel}。只写首次，永不覆盖。 */
public final class ViewerRegisterChannel {

    /** 微信（小程序一键登录 / 手机号授权）。 */
    public static final int WECHAT = 1;

    /** 管理端导入。本轮无导入接口，只预留枚举。 */
    public static final int IMPORT = 2;

    private ViewerRegisterChannel() {
    }
}
