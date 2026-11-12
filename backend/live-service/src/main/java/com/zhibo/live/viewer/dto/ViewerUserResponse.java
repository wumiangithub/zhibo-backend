package com.zhibo.live.viewer.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

/** 登录、刷新、当前观众共用的观众资料。无手机号时不带 phoneMasked。 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ViewerUserResponse {

    /**
     * 用户中心 username（业务用户主键 / JWT sub，D118）。未同步（local 明文登录）则省略。
     * 本站展示昵称仍用 nickname。
     */
    private String username;

    /**
     * 微信小程序 openid（库列 mini_openid）。有则回，供小程序 wx_lite prepay 的 openId；
     * local 明文造号等无凭证时省略。勿当展示字段。
     */
    private String miniOpenid;

    private boolean hasPhone;

    /** 脱敏手机号。没有手机号则省略。 */
    private String phoneMasked;

    private String nickname;

    private String avatarUrl;

    /** 头像 TOS 对象键。没有头像则省略。 */
    private String avatarObjectKey;
}
