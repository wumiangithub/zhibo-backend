package com.zhibo.admin.console.dto;

import java.util.ArrayList;
import java.util.List;
import lombok.Data;

/** {@code GET /api/v1/activities/console/question-history/{id}}。 */
@Data
public class ConsoleQuestionHistoryResponse {

    private long total;
    private List<ConsoleQuestionHistoryItem> list = new ArrayList<>();
}
