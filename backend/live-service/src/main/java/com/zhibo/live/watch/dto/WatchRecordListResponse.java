package com.zhibo.live.watch.dto;

import java.util.ArrayList;
import java.util.List;
import lombok.Data;

@Data
public class WatchRecordListResponse {

    private List<WatchRecordItem> list = new ArrayList<>();
}
