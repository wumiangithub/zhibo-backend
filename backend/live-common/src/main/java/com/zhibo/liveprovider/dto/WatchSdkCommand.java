package com.zhibo.liveprovider.dto;

/**
 * @param viewerId 观众数字 id。有值时微吼 email 为 {@code {viewerId}@qkzb.com} 并参与签名；空则仍用 guestId。
 */
public record WatchSdkCommand(
        String providerRoomId,
        String guestId,
        String nickname,
        Long viewerId
) {
}
