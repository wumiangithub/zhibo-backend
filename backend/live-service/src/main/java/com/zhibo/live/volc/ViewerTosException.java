package com.zhibo.live.volc;

/** 观众头像 TOS 上传失败。 */
public class ViewerTosException extends RuntimeException {

    private final int code;

    public ViewerTosException(int code, String message) {
        super(message);
        this.code = code;
    }

    public int getCode() {
        return code;
    }
}
