package com.zhibo.liveprovider.dto;

import java.util.List;

/** 某场直播下的回放列表。 */
public record ProviderRecordList(
        long total,
        List<ProviderRecordItem> list
) {
}
