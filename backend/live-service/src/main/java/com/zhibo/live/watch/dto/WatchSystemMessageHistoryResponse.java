package com.zhibo.live.watch.dto;

import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class WatchSystemMessageHistoryResponse {
    private List<WatchSystemMessageHistoryItem> list = new ArrayList<>();
    private Long pinnedCommentId;
}
