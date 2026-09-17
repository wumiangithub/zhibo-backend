package com.zhibo.vhall.exception;

import com.zhibo.vhall.dto.VhallResponse;
import lombok.Getter;

/**
 * 调用微吼失败时抛出的运行时异常。
 * <p>
 * 继承 {@link RuntimeException}：不必在方法签名上写 {@code throws}（类比前端 Promise reject，
 * 不一定每个函数都标 {@code async}）。HTTP 网络错误、签名问题、微吼返回非 200，都可以用它包装。
 * <p>
 * 注意：这是「对接微吼」层的异常；两个 service 对外的全局异常处理在 T1.5，会把它转成前端的
 * {@code {code, msg, data}}，不要把本类直接序列化给浏览器。
 */
@Getter
public class VhallException extends RuntimeException {

    /** 微吼业务码；网络层失败时可为 null。 */
    private final Integer code;

    /** 微吼 request_id；没有时可为 null。 */
    private final String requestId;

    public VhallException(String message) {
        this(null, null, message, null);
    }

    public VhallException(String message, Throwable cause) {
        this(null, null, message, cause);
    }

    public VhallException(Integer code, String requestId, String message) {
        this(code, requestId, message, null);
    }

    public VhallException(Integer code, String requestId, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
        this.requestId = requestId;
    }

    /** 从微吼响应构造；msg 为空时给一个可读默认文案。 */
    public static VhallException of(VhallResponse<?> response) {
        if (response == null) {
            return new VhallException("微吼响应为空");
        }
        String msg = response.getMsg();
        if (msg == null || msg.isBlank()) {
            msg = "微吼调用失败, code=" + response.getCode();
        }
        return new VhallException(response.getCode(), response.getRequestId(), msg);
    }
}
