package com.zhibo.liveprovider.dto;

/**
 * @param viewerId 观众数字 id（必填）。微吼 email 为 {@code {viewerId}@qkzb.com} 并参与签名（D92）。
 * @param head     观众头像 URL。无头像为 {@code ""}。微吼 JSSDK {@code head}，参与签名。
 */
public record WatchSdkCommand(
        String providerRoomId,
        String nickname,
        Long viewerId,
        String head
) {
}
