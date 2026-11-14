package com.zhibo.admin.console.dto;

import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ConsoleSystemMessageHistoryResponse {
    private List<ConsoleSystemMessageHistoryItem> list = new ArrayList<>();
    /** 当前置顶 commentId；无则省略/空。 */
    private Long pinnedCommentId;
}
