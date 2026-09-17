package com.zhibo.admin.web;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 管理端对外统一响应。成功 {@code code=0}（见 API约定）。
 * <p>
 * 不要和微吼的 {@code VhallResponse(code=200)} 混用；本类只给前端。
 * 类比：前端 axios 拦截器里认的那一层 {@code { code, msg, data }}。
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
