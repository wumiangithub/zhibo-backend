package com.zhibo.liveprovider.dto;

/**
 * 第三方 rtmp 推流信息。{@code streamAddress} 是服务器，{@code streamCode} 是串流密钥。
 */
public record PushStreamInfo(
        String streamAddress,
        String streamCode,
        String expireTime
) {
}
