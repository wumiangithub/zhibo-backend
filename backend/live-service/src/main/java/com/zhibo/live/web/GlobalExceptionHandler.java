package com.zhibo.live.web;

import com.zhibo.live.viewer.ViewerAuthException;
import com.zhibo.live.volc.ViewerTosException;
import com.zhibo.liveprovider.ProviderNotAvailableException;
import com.zhibo.vhall.exception.VhallException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

/**
 * 观看端全局异常 → {@link ApiResponse}。与 admin-service 对称，各自维护。
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ViewerAuthException.class)
    public ApiResponse<Void> handleViewerAuth(ViewerAuthException ex) {
        log.info("观众鉴权 code={} msg={}", ex.getCode(), ex.getMessage());
        return ApiResponse.fail(ex.getCode(), ex.getMessage());
    }

    @ExceptionHandler(ViewerTosException.class)
    public ApiResponse<Void> handleViewerTos(ViewerTosException ex) {
        log.warn("观众 TOS code={} msg={}", ex.getCode(), ex.getMessage());
        return ApiResponse.fail(ex.getCode(), ex.getMessage());
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ApiResponse<Void> handleMaxUpload(MaxUploadSizeExceededException ex) {
        return ApiResponse.fail(400, "图片不能超过2MB");
    }

    @ExceptionHandler(ProviderNotAvailableException.class)
    public ApiResponse<Void> handleProviderNotAvailable(ProviderNotAvailableException ex) {
        log.info("服务商未接入: {}", ex.getMessage());
        return ApiResponse.fail(501, ex.getMessage());
    }

    @ExceptionHandler(VhallException.class)
    public ApiResponse<Void> handleVhall(VhallException ex) {
        log.warn("微吼调用失败 code={} requestId={} msg={}",
                ex.getCode(), ex.getRequestId(), ex.getMessage());
        int code = ex.getCode() != null ? ex.getCode() : 502;
        return ApiResponse.fail(code, ex.getMessage());
    }

    @ExceptionHandler({
            MethodArgumentNotValidException.class,
            BindException.class,
            HttpMessageNotReadableException.class,
            IllegalArgumentException.class
    })
    public ApiResponse<Void> handleBadRequest(Exception ex) {
        String msg = resolveValidationMessage(ex);
        log.info("请求参数错误: {}", msg);
        return ApiResponse.fail(400, msg);
    }

    @ExceptionHandler(Exception.class)
    public ApiResponse<Void> handleOther(Exception ex) {
        log.error("未处理异常", ex);
        return ApiResponse.fail(500, "服务器错误");
    }

    private static String resolveValidationMessage(Exception ex) {
        if (ex instanceof MethodArgumentNotValidException manv
                && manv.getBindingResult().getFieldError() != null) {
            return manv.getBindingResult().getFieldError().getDefaultMessage();
        }
        if (ex instanceof BindException be
                && be.getBindingResult().getFieldError() != null) {
            return be.getBindingResult().getFieldError().getDefaultMessage();
        }
        if (ex.getMessage() != null && !ex.getMessage().isBlank()) {
            return ex.getMessage();
        }
        return "参数错误";
    }
}
