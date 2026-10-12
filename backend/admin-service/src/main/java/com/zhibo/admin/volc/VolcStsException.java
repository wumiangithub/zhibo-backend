package com.zhibo.admin.volc;

import lombok.Getter;

/**
 * 调用火山 STS 失败。全局异常处理按 {@link #code} 转成业务码（多为 502）。
 */
@Getter
public class VolcStsException extends RuntimeException {

    private final int code;

    public VolcStsException(int code, String message) {
        this(code, message, null);
    }

    public VolcStsException(int code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }
}
