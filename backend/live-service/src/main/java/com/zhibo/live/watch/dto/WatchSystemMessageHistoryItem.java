package com.zhibo.live.watch.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class WatchSystemMessageHistoryItem {
    private Long commentId;
    private String text;
    private boolean pin;
    private String nickname;
    private String commentTime;
    private Long timestamp;
}
