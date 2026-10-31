package com.zhibo.live.watch.dto;

import java.util.ArrayList;
import java.util.List;
import lombok.Data;

/** {@code GET /api/v1/watch/chat-history/{id}} 响应。 */
@Data
public class WatchChatHistoryResponse {

    private long total;
    private int page;
    private int pageSize;
    private List<WatchChatHistoryItem> list = new ArrayList<>();
}
