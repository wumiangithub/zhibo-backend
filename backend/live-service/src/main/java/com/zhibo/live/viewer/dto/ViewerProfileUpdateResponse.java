package com.zhibo.live.viewer.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

/** 资料更新成功 data：只回昵称与头像。 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ViewerProfileUpdateResponse {

    private String nickname;

    private String avatarUrl;
}
