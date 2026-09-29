package com.zhibo.liveprovider.dto;

public record WatchSdkCommand(
        String providerRoomId,
        String guestId,
        String nickname
) {
}
