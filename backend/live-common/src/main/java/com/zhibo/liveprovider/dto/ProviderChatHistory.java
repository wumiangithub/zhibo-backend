package com.zhibo.liveprovider.dto;

import java.util.List;

/** 云侧聊天历史一页（list 顺序由实现决定，观看端再按展示约定处理）。 */
public record ProviderChatHistory(
        long total,
        List<ProviderChatMessage> list
) {
}
