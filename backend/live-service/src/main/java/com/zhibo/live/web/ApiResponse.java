package com.zhibo.live.web;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 观看端对外统一响应。成功 {@code code=0}（见 API约定）。
 * <p>
 * 故意和 admin-service 各写一份，不放进 {@code vhall-common}（后期 copy 微吼库时不应带走项目返回体）。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ApiResponse<T> {

    private int code;
    private String msg;
    private T data;

    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(0, "ok", data);
    }

    public static ApiResponse<Void> ok() {
        return ok(null);
    }

    public static <T> ApiResponse<T> fail(int code, String msg) {
        return new ApiResponse<>(code, msg == null ? "error" : msg, null);
    }
}
