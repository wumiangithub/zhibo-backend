package com.zhibo.admin.auth;

import lombok.Getter;

/** 鉴权/账号状态类业务错误；全局处理按 {@link #code} 返回（401 / 403 等）。 */
@Getter
public class AuthException extends RuntimeException {

    private final int code;

    public AuthException(int code, String message) {
        super(message);
        this.code = code;
    }
}
