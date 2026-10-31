package com.zhibo.liveprovider.dto;

/**
 * @param viewerId 观众数字 id（必填）。微吼 email 为 {@code {viewerId}@qkzb.com} 并参与签名（D92）。
 */
public record WatchSdkCommand(
        String providerRoomId,
        String nickname,
        Long viewerId
) {
}
