package com.zhibo.admin.console.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ConsoleSendSystemMessageRequest {
    /** 系统消息正文，1～200 字。 */
    private String text;
    /** 是否置顶；同场仅一条 pin=true。 */
    private Boolean pin;
}
