package com.zhibo.live.watch.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class WatchActivityListResponse {

    private Long total;
    private List<WatchActivityItem> list = new ArrayList<>();
}
