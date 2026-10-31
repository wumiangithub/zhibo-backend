package com.zhibo.liveprovider.dto;

/**
 * 云侧历史聊天一条（已归一，供观看端回放只读列表）。
 */
public record ProviderChatMessage(
        String senderId,
        String senderName,
        String msg,
        String msgId,
        String avatar,
        /** 1 主持 / 2 观众 / 3 助理 / 4 嘉宾；可空 */
        Integer roleName,
        /** 毫秒时间戳 */
        long timestamp
) {
}
