package com.zhibo.live.viewer.dto;

import lombok.Data;

/** 更新观众昵称 / 头像 URL。至少填一项。 */
@Data
public class ViewerProfileUpdateRequest {

    /** 1～16 字；省略不改。 */
    private String nickname;

    /** http(s) 本站 CDN/桶 URL；省略不改。 */
    private String avatarUrl;
}
