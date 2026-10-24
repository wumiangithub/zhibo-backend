package com.zhibo.liveprovider.dto;

public record ProviderRoomSnapshot(
        String title,
        Integer state,
        Integer type,
        Integer pv,
        String fullEmbedShareLink,
        String startTime,
        String endTime,
        String introduction,
        Integer verify,
        String coverUrl
) {
}
