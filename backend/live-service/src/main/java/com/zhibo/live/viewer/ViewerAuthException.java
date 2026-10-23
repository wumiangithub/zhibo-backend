package com.zhibo.live.viewer;

import lombok.Getter;

/** 观众鉴权 / 登录业务错误。全局处理按 {@link #code} 返回，HTTP 仍是 200。 */
@Getter
public class ViewerAuthException extends RuntimeException {

    private final int code;

    public ViewerAuthException(int code, String message) {
        super(message);
        this.code = code;
    }
}
