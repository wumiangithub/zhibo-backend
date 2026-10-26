package com.zhibo.live.viewer.dto;

import lombok.Data;

/** 更新观众昵称 / 头像 URL。至少填一项。 */
@Data
public class ViewerProfileUpdateRequest {

    /** 1～16 字；省略不改。 */
    private String nickname;

    /** 访问地址，与 avatarObjectKey 成对出现。 */
    private String avatarUrl;

    /** TOS 对象键，须以 viewer/avatar/ 开头，且是 avatarUrl 的路径。 */
    private String avatarObjectKey;
}
