package com.zhibo.vhall.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.zhibo.vhall.exception.VhallException;
import lombok.Data;

/**
 * 微吼开放平台统一响应体。
 * <p>
 * 形如 {@code { "code": 200, "msg": "success", "data": {...}, "request_id": "..." }}。
 * 注意：微吼成功是 {@code code=200}；我们对外给前端的 {@code ApiResponse} 成功是 {@code code=0}，
 * 两套不要混（{@code ApiResponse} 不进本模块）。
 * <p>
 * 类比前端：axios 的 {@code response.data} 类型；{@link #isSuccess()} 类似自己判断 {@code code === 200}。
 *
 * @param <T> data 字段的业务类型
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class VhallResponse<T> {

    /** 微吼业务码：200 表示成功。 */
    private Integer code;

    private String msg;

    private T data;

    /** 微吼链路追踪 id，排障时有用。JSON 字段名是 request_id。 */
    @JsonProperty("request_id")
    private String requestId;

    public boolean isSuccess() {
        return code != null && code == 200;
    }

    /**
     * 成功则返回 data，否则抛 {@link VhallException}。
     * 类比：{@code if (res.code !== 200) throw ...; return res.data}。
     */
    public T requireData() {
        if (!isSuccess()) {
            throw VhallException.of(this);
        }
        return data;
    }
}
