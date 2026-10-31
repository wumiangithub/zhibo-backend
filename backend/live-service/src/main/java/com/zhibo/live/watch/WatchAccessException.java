package com.zhibo.live.watch;

/** 观看售卖门禁失败（业务 code，默认 403）。 */
public class WatchAccessException extends RuntimeException {

    private final int code;

    public WatchAccessException(String message) {
        this(403, message);
    }

    public WatchAccessException(int code, String message) {
        super(message);
        this.code = code;
    }

    public int getCode() {
        return code;
    }
}
