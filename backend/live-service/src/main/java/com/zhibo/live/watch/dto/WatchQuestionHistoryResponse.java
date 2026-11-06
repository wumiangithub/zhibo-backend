package com.zhibo.live.watch.dto;

import java.util.ArrayList;
import java.util.List;
import lombok.Data;

/** {@code GET /api/v1/watch/question-history/{id}}。 */
@Data
public class WatchQuestionHistoryResponse {

    private long total;
    private List<WatchQuestionHistoryItem> list = new ArrayList<>();
}
