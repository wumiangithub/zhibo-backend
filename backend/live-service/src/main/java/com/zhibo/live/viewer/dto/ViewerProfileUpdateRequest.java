package com.zhibo.live.viewer.dto;

import lombok.Data;

/** 更新观众昵称 / 头像 URL。至少填一项。 */
@Data
public class ViewerProfileUpdateRequest {

    /** 1～16 字；省略不改。 */
    private String nickname;

    /** 访问地址，与 avatarObjectKey 成对；校验通过后写入 avatar_url。 */
    private String avatarUrl;

    /** TOS 对象键；与 avatarUrl 成对校验，不落库。 */
    private String avatarObjectKey;
}
