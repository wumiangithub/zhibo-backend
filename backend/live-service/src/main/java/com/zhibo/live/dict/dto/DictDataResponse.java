package com.zhibo.live.dict.dto;

import java.util.ArrayList;
import java.util.List;
import lombok.Data;

/** {@code GET /api/v1/dicts/data/{dictDm}} 响应。 */
@Data
public class DictDataResponse {

    private String dictDm;
    private String dictName;
    private List<DictDataItem> list = new ArrayList<>();
}
