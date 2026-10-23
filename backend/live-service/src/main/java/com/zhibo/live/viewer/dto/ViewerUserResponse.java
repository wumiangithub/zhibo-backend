package com.zhibo.live.viewer.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

/** 登录、刷新、当前观众共用的观众资料。无手机号时不带 phoneMasked。 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ViewerUserResponse {

    /** viewer.id，JSON 数字。 */
    private Long id;

    private boolean hasPhone;

    /** 脱敏手机号。没有手机号则省略。 */
    private String phoneMasked;

    private String nickname;

    private String avatarUrl;
}
